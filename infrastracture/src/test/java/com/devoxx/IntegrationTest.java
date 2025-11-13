package com.devoxx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

public class IntegrationTest extends IntegrationBaseTest {

    private static SqsClient sqs;
    private static DynamoDbClient dynamoDb;

    @BeforeAll
    public static void setUp() throws JsonProcessingException {
        deployCdkStackToLocalStack();
        sqs = sqsClient();
        dynamoDb = dynamoDbClient();
    }

    @Test
    public void shouldSendMessageToSqsAndStoreInDynamoDb() throws JsonProcessingException {
        String companyId = "devoxx-" + UUID.randomUUID();
        String uuid = UUID.randomUUID().toString();

        ObjectMapper objectMapper = new ObjectMapper();

        Item item = new Item(
                uuid,
                companyId,
                "Laptop",
                1299.99,
                5
        );

        String message = objectMapper.writeValueAsString(item);

        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl("http://sqs.eu-central-1.localhost:4566/000000000000/ItemQueue")
                .messageBody(message)
                .build());

        await()
                .pollInterval(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> {
                    var response = dynamoDb.getItem(GetItemRequest.builder()
                            .tableName("Items")
                            .key(Map.of(
                                    "companyId", AttributeValue.fromS(companyId),
                                    "uuid", AttributeValue.fromS(uuid)
                            ))
                            .build());

                    Assertions.assertFalse(response.item().isEmpty());
                });
    }
}
