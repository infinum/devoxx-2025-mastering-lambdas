package com.devoxx;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSource;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

import java.util.Map;

public class InfraStack extends Stack {

    public InfraStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
        String path = System.getenv("LAMBDA_PATH") != null ? System.getenv("LAMBDA_PATH") : "devoxxlambda-1.0.0.jar";
                String endpoint = System.getProperty("ENDPOINT");
        String systemTest = System.getenv("SYSTEM_TEST") != null ? System.getenv("SYSTEM_TEST") : System.getProperty("SYSTEM_TEST");

        Table table = createTable();

        Queue queue = createSqsQueue();

        Function.Builder lambdaBuilder = Function.Builder.create(this, "DevoxxLambda")
                .functionName("DevoxxLambda")
                .runtime(Runtime.JAVA_17)
                .handler("com.devoxx.DevoxxLambda::handleRequest")
                .memorySize(1024)
                .timeout(Duration.seconds(30))
                .environment(createMap(endpoint, table));

        if (systemTest == null) {
            Function lambda = lambdaBuilder
                    .code(determinePath(systemTest, path, null))
                    .architecture(Architecture.ARM_64)
                    .snapStart(SnapStartConf.ON_PUBLISHED_VERSIONS).build();
            Version version = Version.Builder.create(this, "JavaLambdaVersion1")
                    .lambda(lambda)
                    .description("Published AWS Lambda Version")
                    .build();


            Alias alias = Alias.Builder.create(this, "ProdAlias")
                    .aliasName("prod")
                    .version(version)
                    .build();

            addIamRightsAndSubscribeAliasToSqs(table, alias, queue);
        } else {
            Bucket lambdaBucket = createJarLambda();
            Function lambda = lambdaBuilder.code(determinePath(systemTest, path, lambdaBucket)).build();
            addIamRightsAndSubscribeLambdaToSqs(table, lambda, queue, lambdaBucket);
        }

    }

    @NotNull
    private static Code determinePath(String systemTest, String path, Bucket lambdaBucket) {
        return systemTest == null ? Code.fromAsset(path) : Code.fromBucket(lambdaBucket, "devoxxlambda-1.0.0.jar");
    }

    private Map<String, String> createMap(String endpoint, Table table) {
        if (endpoint != null) {
           return Map.of(
                    "TABLE_NAME", table.getTableName(),
                    "ENDPOINT", endpoint,
                    "JAVA_OPTIONS", "-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
            );
        } else {
            return Map.of(
                    "TABLE_NAME", table.getTableName(),
                    "JAVA_OPTIONS", "-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
            );
        }

    }

    @NotNull
    private Bucket createJarLambda() {
        return Bucket.Builder.create(this, "LambdaBucket")
                .bucketName("devoxx-lambda-bucket-2025-unknown")
                .build();
    }

    @NotNull
    private Queue createSqsQueue() {
        return Queue.Builder.create(this, "ItemQueue")
                .queueName("ItemQueue")
                .visibilityTimeout(Duration.seconds(30))
                .build();
    }

    private Table createTable() {
        return Table.Builder.create(this, "ItemsTable")
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
    }

    private void addIamRightsAndSubscribeAliasToSqs(Table table, Alias alias, Queue queue) {
        table.grantReadWriteData(alias);
        queue.grantConsumeMessages(alias);

        alias.addEventSource(new SqsEventSource(queue));

        software.amazon.awscdk.CfnOutput.Builder.create(this, "TableName")
                .value(table.getTableName())
                .build();

        software.amazon.awscdk.CfnOutput.Builder.create(this, "QueueUrl")
                .value(queue.getQueueUrl())
                .build();
    }

    private void addIamRightsAndSubscribeLambdaToSqs(Table table, Function lambda, Queue queue, Bucket lambdaBucket) {
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