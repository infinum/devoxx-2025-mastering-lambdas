package com.devoxx;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

public class DevoxxLambda implements RequestHandler<SQSEvent, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbEnhancedClient enhancedClient;

    public DevoxxLambda() {
        // Read environment variables
        String dynamoEndpoint = System.getenv("DYNAMODB_ENDPOINT");
        TABLE_NAME = System.getenv("TABLE_NAME");

        // Build DynamoDbClient pointing to LocalStack
        dynamoDbClient = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://localstack:4566"))
                .region(Region.EU_CENTRAL_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test"))) // LocalStack dummy creds
                .build();

        enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
    private static String TABLE_NAME = System.getenv("TABLE_NAME");

    @Override
    public String handleRequest(SQSEvent event, Context context) {
        context.getLogger().log("Received " + event.getRecords().size() + " messages\n");

        DynamoDbTable<Item> itemTable = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(Item.class));

        event.getRecords().forEach(msg -> {
            try {
                Item item = objectMapper.readValue(msg.getBody(), Item.class);
                itemTable.putItem(item);
                context.getLogger().log("Saved: " + item + "\n");
            } catch (Exception e) {
                context.getLogger().log("Error processing message: " + e.getMessage() + "\n");
            }
        });

        return "Processed " + event.getRecords().size() + " messages.";
    }
}