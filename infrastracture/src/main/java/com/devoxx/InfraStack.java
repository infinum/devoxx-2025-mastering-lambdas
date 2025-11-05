package com.devoxx;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSource;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

import java.util.Map;

public class InfraStack extends Stack {

    public InfraStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
        String endpoint = System.getProperty("ENDPOINT");
        Table table = Table.Builder.create(this, "ItemsTable")
                .tableName("Items")
                .partitionKey(Attribute.builder()
                        .name("companyId")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("uuid")
                        .type(AttributeType.STRING)
                        .build())
                .build();

        Queue queue = Queue.Builder.create(this, "ItemQueue")
                .queueName("ItemQueue")
                .visibilityTimeout(Duration.seconds(30))
                .build();

        Bucket lambdaBucket = Bucket.Builder.create(this, "LambdaBucket")
                .bucketName("devoxx-lambda-bucket")
                .build();


        Function lambda = Function.Builder.create(this, "DevoxxLambda")
                .functionName("DevoxxLambda")
                .runtime(Runtime.JAVA_17)
                .handler("com.devoxx.DevoxxLambda::handleRequest")
                .code(Code.fromBucket(lambdaBucket, "devoxxlambda-1.0.0.jar"))
                .memorySize(1024)
                .timeout(Duration.seconds(30))
                .environment(Map.of(
                        "TABLE_NAME", table.getTableName(),
                        "DYNAMODB_ENDPOINT", endpoint,
                        "SQS_ENDPOINT", endpoint
                ))
                .build();

        table.grantReadWriteData(lambda);
        queue.grantConsumeMessages(lambda);

        lambda.addEventSource(new SqsEventSource(queue));

        software.amazon.awscdk.CfnOutput.Builder.create(this, "TableName")
                .value(table.getTableName())
                .build();

        software.amazon.awscdk.CfnOutput.Builder.create(this, "QueueUrl")
                .value(queue.getQueueUrl())
                .build();

        software.amazon.awscdk.CfnOutput.Builder.create(this, "LambdaBucketName")
                .value(lambdaBucket.getBucketName())
                .build();
    }
}