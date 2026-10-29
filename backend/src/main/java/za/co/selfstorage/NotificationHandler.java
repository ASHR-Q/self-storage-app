package za.co.selfstorage;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

public class NotificationHandler implements RequestHandler<DynamodbEvent, Void> {
    private static final SnsClient SNS = SnsClient.create();
    private static final String TOPIC_ARN = requiredEnvironment("NOTIFICATION_TOPIC_ARN");

    @Override
    public Void handleRequest(DynamodbEvent event, Context context) {
        for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {
            if (!"INSERT".equals(record.getEventName()) || record.getDynamodb() == null) {
                continue;
            }
            Map<String, AttributeValue> image = record.getDynamodb().getNewImage();
            if (image == null || !"RESERVATION".equals(value(image, "entity"))) {
                continue;
            }
            String message = "A reservation was created for unit " + value(image, "unitId")
                    + " from " + value(image, "startDate") + " to " + value(image, "endDate") + ".";
            SNS.publish(PublishRequest.builder()
                    .topicArn(TOPIC_ARN)
                    .subject("New self-storage reservation")
                    .message(message)
                    .build());
        }
        return null;
    }

    private static String value(Map<String, AttributeValue> image, String key) {
        AttributeValue attribute = image.get(key);
        return attribute == null ? "" : attribute.getS();
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required");
        }
        return value;
    }
}
