package com.devoxx;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

public class AwsSdkClientUtil {

    public static DynamoDbClient createDynamoDbClient() {
        //For system and unit tests
        String endpoint = System.getenv("ENDPOINT") != null ? System.getenv("ENDPOINT") : System.getProperty("ENDPOINT") ;

        DynamoDbClientBuilder builder = DynamoDbClient.builder();
        if (endpoint != null) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
