package com.devoxx;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

public class AwsSdkClientUtil {

    public static DynamoDbClient createDynamoDbClient() {
        String endpoint = System.getenv("ENDPOINT");
        DynamoDbClientBuilder builder = DynamoDbClient.builder();
        if (endpoint != null) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
