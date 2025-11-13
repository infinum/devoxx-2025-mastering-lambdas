package com.devoxx;

import software.amazon.awscdk.App;

public class Main {
    public static void main(final String[] args) {
        App app = new App();
        new InfraStack(app, "InfraStack", null);
        app.synth();
    }
}
