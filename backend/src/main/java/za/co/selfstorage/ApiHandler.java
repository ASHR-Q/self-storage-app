package za.co.selfstorage;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ApiHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private static final Logger LOGGER = Logger.getLogger(ApiHandler.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DynamoDbClient DYNAMO = DynamoDbClient.create();
    private static final String TABLE_NAME = requiredEnvironment("TABLE_NAME");

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String method = event.getRequestContext().getHttp().getMethod();
        String path = event.getRawPath();
        try {
            if ("GET".equals(method) && "/units".equals(path)) {
                return response(200, listUnits());
            }

            String customerId = customerId(event);
            if ("GET".equals(method) && "/reservations".equals(path)) {
                return response(200, listReservations(customerId));
            }
            if ("POST".equals(method) && "/reservations".equals(path)) {
                return createReservation(customerId, event.getBody());
            }
            if ("DELETE".equals(method) && path.matches("/reservations/[^/]+")) {
                return cancelReservation(customerId, path.substring(path.lastIndexOf('/') + 1));
            }
            return response(404, Map.of("message", "Route not found"));
        } catch (IllegalArgumentException | JsonProcessingException exception) {
            return response(400, Map.of("message", exception.getMessage()));
        } catch (TransactionCanceledException exception) {
            return response(409, Map.of("message", "This space changed while you were booking. Please retry."));
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Request failed", exception);
            return response(500, Map.of("message", "The request could not be completed"));
        }
    }

    private static List<Map<String, Object>> listUnits() {
        ScanRequest request = ScanRequest.builder()
                .tableName(TABLE_NAME)
                .filterExpression("entity = :unit")
                .expressionAttributeValues(Map.of(":unit", stringValue("UNIT")))
                .build();
        List<Map<String, Object>> units = new ArrayList<>();
        DYNAMO.scanPaginator(request).items().forEach(item -> units.add(Map.of(
                "unitId", item.get("unitId").s(),
                "size", item.get("size").s(),
                "monthlyPrice", item.get("monthlyPrice").n(),
                "available", Boolean.parseBoolean(item.get("available").bool().toString())
        )));
        return units;
    }

    private static List<Map<String, Object>> listReservations(String customerId) {
        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :customer")
                .expressionAttributeValues(Map.of(":customer", stringValue("CUSTOMER#" + customerId)))
                .build();
        List<Map<String, Object>> reservations = new ArrayList<>();
        DYNAMO.queryPaginator(request).items().stream()
                .filter(item -> "RESERVATION".equals(item.get("entity").s()))
                .forEach(item -> reservations.add(reservationJson(item)));
        return reservations;
    }

    private static APIGatewayV2HTTPResponse createReservation(String customerId, String body)
            throws JsonProcessingException {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Request body is required");
        }
        JsonNode input = JSON.readTree(body);
        String unitId = requiredText(input, "unitId");
        LocalDate startDate = parseDate(requiredText(input, "startDate"), "startDate");
        LocalDate endDate = parseDate(requiredText(input, "endDate"), "endDate");
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("endDate must be after startDate");
        }

        Map<String, AttributeValue> unit = DYNAMO.getItem(builder -> builder
                .tableName(TABLE_NAME)
                .key(Map.of("PK", stringValue("UNIT#" + unitId), "SK", stringValue("UNIT")))
                .consistentRead(true)).item();
        if (unit.isEmpty() || !"true".equals(unit.get("available").bool().toString())) {
            throw new IllegalArgumentException("Storage unit is not available");
        }
        long lockVersion = getLockVersion(unitId);
        ensureNoOverlap(unitId, startDate, endDate);

        String reservationId = UUID.randomUUID().toString();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", stringValue("CUSTOMER#" + customerId));
        item.put("SK", stringValue("RESERVATION#" + reservationId));
        item.put("entity", stringValue("RESERVATION"));
        item.put("reservationId", stringValue(reservationId));
        item.put("customerId", stringValue(customerId));
        item.put("unitId", stringValue(unitId));
        item.put("startDate", stringValue(startDate.toString()));
        item.put("endDate", stringValue(endDate.toString()));
        item.put("status", stringValue("PENDING"));
        Map<String, AttributeValue> unitBooking = new HashMap<>(item);
        unitBooking.put("PK", stringValue("UNIT#" + unitId));
        unitBooking.put("SK", stringValue("BOOKING#" + reservationId));
        unitBooking.put("entity", stringValue("UNIT_RESERVATION"));
        DYNAMO.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(
                        lockVersionUpdate(unitId, lockVersion),
                        TransactWriteItem.builder().put(Put.builder()
                                .tableName(TABLE_NAME)
                                .item(item)
                                .conditionExpression("attribute_not_exists(PK)")
                                .build()).build(),
                        TransactWriteItem.builder().put(Put.builder()
                                .tableName(TABLE_NAME)
                                .item(unitBooking)
                                .conditionExpression("attribute_not_exists(PK)")
                                .build()).build())
                .build());
        return response(201, reservationJson(item));
    }

    private static void ensureNoOverlap(String unitId, LocalDate startDate, LocalDate endDate) {
        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .consistentRead(true)
                .keyConditionExpression("PK = :unit AND begins_with(SK, :booking)")
                .filterExpression("entity = :unitReservation AND endDate > :start AND startDate < :end AND #status <> :cancelled")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":unit", stringValue("UNIT#" + unitId),
                        ":booking", stringValue("BOOKING#"),
                        ":start", stringValue(startDate.toString()),
                        ":end", stringValue(endDate.toString()),
                        ":unitReservation", stringValue("UNIT_RESERVATION"),
                        ":cancelled", stringValue("CANCELLED")))
                .build();
        if (DYNAMO.queryPaginator(request).items().iterator().hasNext()) {
            throw new IllegalArgumentException("Storage unit is already reserved for these dates");
        }
    }

    private static APIGatewayV2HTTPResponse cancelReservation(String customerId, String reservationId) {
        Map<String, AttributeValue> key = Map.of(
                "PK", stringValue("CUSTOMER#" + customerId),
                "SK", stringValue("RESERVATION#" + reservationId));
        Map<String, AttributeValue> reservation = DYNAMO.getItem(builder -> builder
                .tableName(TABLE_NAME)
                .key(key)
                .consistentRead(true)).item();
        if (reservation.isEmpty() || !"RESERVATION".equals(reservation.get("entity").s())
                || "CANCELLED".equals(reservation.get("status").s())) {
            return response(404, Map.of("message", "Reservation not found or already cancelled"));
        }
        String unitId = reservation.get("unitId").s();
        long lockVersion = getLockVersion(unitId);
        Map<String, String> statusName = Map.of("#status", "status");
        Map<String, AttributeValue> statusValues = Map.of(
                ":cancelled", stringValue("CANCELLED"),
                ":reservation", stringValue("RESERVATION"));
        DYNAMO.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(
                        lockVersionUpdate(unitId, lockVersion),
                        TransactWriteItem.builder().update(Update.builder()
                                .tableName(TABLE_NAME)
                                .key(key)
                                .updateExpression("SET #status = :cancelled")
                                .conditionExpression("entity = :reservation AND #status <> :cancelled")
                                .expressionAttributeNames(statusName)
                                .expressionAttributeValues(statusValues)
                                .build()).build(),
                        TransactWriteItem.builder().update(Update.builder()
                                .tableName(TABLE_NAME)
                                .key(Map.of(
                                        "PK", stringValue("UNIT#" + unitId),
                                        "SK", stringValue("BOOKING#" + reservationId)))
                                .updateExpression("SET #status = :cancelled")
                                .conditionExpression("entity = :unitReservation AND #status <> :cancelled")
                                .expressionAttributeNames(statusName)
                                .expressionAttributeValues(Map.of(
                                        ":cancelled", stringValue("CANCELLED"),
                                        ":unitReservation", stringValue("UNIT_RESERVATION")))
                                .build()).build())
                .build());
        return response(204, Map.of());
    }

    private static long getLockVersion(String unitId) {
        Map<String, AttributeValue> lock = DYNAMO.getItem(builder -> builder
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", stringValue("UNIT#" + unitId),
                        "SK", stringValue("LOCK")))
                .consistentRead(true)).item();
        AttributeValue version = lock.get("version");
        return version == null ? 0 : Long.parseLong(version.n());
    }

    private static TransactWriteItem lockVersionUpdate(String unitId, long currentVersion) {
        return TransactWriteItem.builder().update(Update.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", stringValue("UNIT#" + unitId),
                        "SK", stringValue("LOCK")))
                .updateExpression("SET #version = :next")
                .conditionExpression("attribute_not_exists(#version) OR #version = :current")
                .expressionAttributeNames(Map.of("#version", "version"))
                .expressionAttributeValues(Map.of(
                        ":current", AttributeValue.builder().n(Long.toString(currentVersion)).build(),
                        ":next", AttributeValue.builder().n(Long.toString(currentVersion + 1)).build()))
                .build()).build();
    }

    private static Map<String, Object> reservationJson(Map<String, AttributeValue> item) {
        return Map.of(
                "reservationId", item.get("reservationId").s(),
                "unitId", item.get("unitId").s(),
                "startDate", item.get("startDate").s(),
                "endDate", item.get("endDate").s(),
                "status", item.get("status").s());
    }

    private static String customerId(APIGatewayV2HTTPEvent event) {
        Map<String, String> claims = event.getRequestContext()
                .getAuthorizer().getJwt().getClaims();
        String subject = claims == null ? null : claims.get("sub");
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Authenticated customer identity is missing");
        }
        return subject;
    }

    private static String requiredText(JsonNode input, String field) {
        JsonNode value = input == null ? null : input.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.asText().trim();
    }

    private static LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be an ISO date (YYYY-MM-DD)");
        }
    }

    private static AttributeValue stringValue(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static APIGatewayV2HTTPResponse response(int status, Object body) {
        try {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(status)
                    .withHeaders(Map.of("content-type", "application/json"))
                    .withBody(JSON.writeValueAsString(body))
                    .build();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize API response", exception);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required");
        }
        return value;
    }
}
