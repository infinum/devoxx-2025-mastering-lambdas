package com.devoxx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.LegacyStackSynthesizer;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.CreateStackRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.nio.file.Path;

@Testcontainers
public class IntegrationBaseTest {
    public static InfraStack stack;
    public static S3Client s3;
    private static final String stackName = "InfraStackTest";

    private static final Network network = Network.builder().createNetworkCmdModifier(cmd -> cmd.withName("localstack-network")).build();
    @Container
    private static final LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.1.0"))
            .withNetwork(network)
            .withNetworkAliases("localstack")
            .withExposedPorts(4566);

    @AfterAll
    public static void clean() {
        network.close();
    }

    public static void deployCdkStackToLocalStack() throws JsonProcessingException {
        String path = System.getenv("LAMBDA_PATH");
        if (path == null) {
         path = "../devoxx-lambda-processor/target/devoxxlambda-1.0.0.jar";
        }
        s3 = s3Client();
        System.setProperty("ENDPOINT", "http://localstack:4566");
        System.setProperty("SYSTEM_TEST", "false");
        String bucketName = "devoxx-lambda-bucket-2025-unknown";
        s3.createBucket(b -> b.bucket(bucketName));

        Path jarFile = Path.of(path);
        s3.putObject(b -> b.bucket(bucketName).key("devoxxlambda-1.0.0.jar"),
                RequestBody.fromFile(jarFile));


        App app = new App();
        StackProps props = StackProps.builder()
                .env(Environment.builder()
                        .account("000000000000")
                        .region(Region.EU_CENTRAL_1.toString())
                        .build())
                .synthesizer(new LegacyStackSynthesizer())
                .build();

        stack = new InfraStack(app, "InfraStackTest", props);

        Template cdkTemplate = Template.fromStack(stack);
        ObjectMapper mapper = new ObjectMapper();
        String templateBody = mapper.writeValueAsString(cdkTemplate.toJSON());

        CloudFormationClient cfClient = createCfnClient();

        try {
            cfClient.createStack(CreateStackRequest.builder()
                    .stackName(stackName)
                    .templateBody(templateBody)
                    .capabilitiesWithStrings("CAPABILITY_NAMED_IAM")
                    .build());

            cfClient.waiter().waitUntilStackCreateComplete(b -> b.stackName(stackName));
        } catch (Exception e) {
            e.printStackTrace();
            Assertions.fail("Failed to deploy CDK stack to LocalStack");
        }
    }

    private static CloudFormationClient createCfnClient() {
        return CloudFormationClient.builder()
                .endpointOverride(localstack.getEndpoint())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
                ))
                .region(Region.of("eu-central-1"))
                .build();
    }

    public static S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(localstack.getEndpoint())
                .region(Region.of("eu-central-1"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();
    }

    public static SqsClient sqsClient() {
        return SqsClient.builder()
                .endpointOverride(localstack.getEndpoint())
                .region(Region.of("eu-central-1"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();
    }

    public static DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .endpointOverride(localstack.getEndpoint())
                .region(Region.of("eu-central-1"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();
    }
}
