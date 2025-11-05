# lambda-devoxx-morocco-2025


steps of ci/cd

1. Build [devoxx-lambda-processor](devoxx-lambda-processor) jar and store it as artifact. Classic mvn package is enough we want test to be run as well.
2. Run [infrastracture](infrastracture) mvn test but we have to pass LAMBDA_PATH as env variable or property variable so we can reference it in S3 upload we have in test. This path is path of previous artifactory step.
3. CDK deploy we can skip this one for now I will just explain to people how it is done.