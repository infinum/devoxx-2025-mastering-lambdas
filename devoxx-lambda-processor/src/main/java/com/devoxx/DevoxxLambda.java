package com.devoxx;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.crac.Resource;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.lambda.powertools.logging.Logging;

public class DevoxxLambda implements RequestHandler<SQSEvent, String>, Resource {

    private static final Logger LOGGER = LogManager.getLogger(DevoxxLambda.class);
    private static final String TABLE_NAME = System.getenv("TABLE_NAME") != null ? System.getenv("TABLE_NAME") : "Items";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbEnhancedClient enhancedClient;

    private static DynamoDbTable<Item> itemTable;

    public DevoxxLambda() {
        dynamoDbClient = AwsSdkClientUtil.createDynamoDbClient();
        enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        itemTable = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(Item.class));
    }


    @Logging(logEvent = true)
    @Override
    public String handleRequest(SQSEvent event, Context context) {
        LOGGER.info("Processing {} messages from SQS", event.getRecords().size());


        event.getRecords().forEach(record -> {
            try {
                Item item = objectMapper.readValue(record.getBody(), Item.class);
                itemTable.putItem(item);
                LOGGER.info("Saved item: {}", item);
            } catch (Exception e) {
                LOGGER.error("Error processing message", e);
            }
        });

        LOGGER.debug("SQS event processing complete");
        return null;
    }

    @Override
    public void beforeCheckpoint(org.crac.Context<? extends Resource> context) throws Exception {

    }

    @Override
    public void afterRestore(org.crac.Context<? extends Resource> context) throws Exception {

    }

    // For Unit test mocking purposes
    public DevoxxLambda(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
        this.enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
}