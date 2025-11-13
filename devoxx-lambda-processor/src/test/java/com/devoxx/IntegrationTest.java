package com.devoxx;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

@Testcontainers
class IntegrationTest {

    private static SqsClient sqs;
    private static DynamoDbClient dynamoDb;
    private static String queueUrl;

    private static final Network network = Network.builder().createNetworkCmdModifier(cmd -> cmd.withName("localstack-network")).build();

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.1.0"))
            .withNetwork(network)
            .withNetworkAliases("localstack")
            .withExposedPorts(4566);

    @BeforeEach
    public void setUp() {
        System.setProperty("aws.region", "eu-central-1");
        sqs = SqsClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
                .region(Region.of("eu-central-1"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();

        dynamoDb = DynamoDbClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
                .region(Region.of("eu-central-1"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();
        String queueName = "ItemQueue";
        sqs.createQueue(b -> b.queueName(queueName));
        dynamoDb.createTable(b -> b.tableName("Items")
                .attributeDefinitions(
                        a -> a.attributeName("companyId").attributeType("S"),
                        a -> a.attributeName("uuid").attributeType("S")
                )
                .keySchema(
                        k -> k.attributeName("companyId").keyType("HASH"),
                        k -> k.attributeName("uuid").keyType("RANGE")
                )
                .provisionedThroughput(p -> p
                        .readCapacityUnits(5L)
                        .writeCapacityUnits(5L)
                ));

        queueUrl = sqs.getQueueUrl(b -> b.queueName(queueName)).queueUrl();
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
                .queueUrl(queueUrl)
                .messageBody(message)
                .build());

        var messages = sqs.receiveMessage(b -> b.queueUrl(queueUrl).maxNumberOfMessages(1)).messages();
        var sqsEvent = new SQSEvent();
        for (var m : messages) {
            SQSEvent.SQSMessage sqsMessage = new SQSEvent.SQSMessage();
            sqsMessage.setBody(m.body());
            sqsEvent.setRecords(List.of(sqsMessage));
        }

        DevoxxLambda handler = new DevoxxLambda(dynamoDb);
        handler.handleRequest(sqsEvent, null);

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
