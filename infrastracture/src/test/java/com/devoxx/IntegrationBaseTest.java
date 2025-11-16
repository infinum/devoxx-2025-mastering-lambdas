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
    public static final ObjectMapper mapper = new ObjectMapper();

    // We have to use network mode here since AWS Lambda will be created as another container
    // To reach SQS EventSource Mapping and DynamoDB communication it will need to call Localstack Container
    // We need container to container communication
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
        //Setting property variables so they can be used in InfraStack
        System.setProperty("ENDPOINT", "http://localstack:4566");
        System.setProperty("SYSTEM_TEST", "true");
        createS3BucketAndUploadJarToIt();
        createStack();

        String templateBody = convertStackToJson();

        CloudFormationClient cfClient = createCfnClient();

        // Create Stack on Localstack Testcontainer. Notice how Lambda container will be created and SQS
        // EventSource mapping will be set correctly meaning Cfn deployment on Localstack is working!
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

    private static String convertStackToJson() throws JsonProcessingException {
        Template cdkTemplate = Template.fromStack(stack);
        return mapper.writeValueAsString(cdkTemplate.toJSON());
    }

    //We create CDK stack which will be later used to as CFN Json template for createStack() method
    private static void createStack() {
        App app = new App();
        StackProps props = StackProps.builder()
                .env(Environment.builder()
                        .account("000000000000")
                        .region(Region.EU_CENTRAL_1.toString())
                        .build())
                .synthesizer(new LegacyStackSynthesizer())
                .build();

        stack = new InfraStack(app, "InfraStackTest", props);
    }

    //We have to create S3 bucket since Code.fromAsset will cause the issues when we do Cloudformation createStack()
    //Reason behind this is that CloudFormation will look for S3 metadata and need internal S3 bucket to create Function
    private static void createS3BucketAndUploadJarToIt() {
        String path = System.getenv("LAMBDA_PATH");
        if (path == null) {
         path = "../devoxx-lambda-processor/target/devoxxlambda-1.0.0.jar";
        }
        s3 = s3Client();
        String bucketName = "devoxx-lambda-bucket-2025-unknown";
        s3.createBucket(b -> b.bucket(bucketName));

        Path jarFile = Path.of(path);
        s3.putObject(b -> b.bucket(bucketName).key("devoxxlambda-1.0.0.jar"),
                RequestBody.fromFile(jarFile));
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
