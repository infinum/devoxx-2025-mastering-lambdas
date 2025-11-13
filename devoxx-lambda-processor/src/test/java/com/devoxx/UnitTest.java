package com.devoxx;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.tests.annotations.Events;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.mockito.Mock;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class UnitTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    private DevoxxLambda devoxxLambda;

    @BeforeEach
    public void setup() {
        System.setProperty("aws.region", "eu-central-1");
        devoxxLambda = new DevoxxLambda(dynamoDbClient);
    }

    @ParameterizedTest
    @Events(folder = "events", type = SQSEvent.class)
    public void handleRequest_GivenValidEvent_ShouldReturnNull(SQSEvent sqsEvent) {
        String result = this.devoxxLambda.handleRequest(sqsEvent, null);
        assert result == null;
    }


}
