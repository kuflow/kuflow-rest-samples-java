/*
 * Copyright (c) 2021-present KuFlow S.L.
 *
 * All rights reserved.
 */

package com.kuflow.rest.samples.worker.loan;

import com.kuflow.rest.client.KuFlowRestClient;
import com.kuflow.rest.client.controller.AuthenticationApi;
import com.kuflow.rest.client.controller.ProcessApi;
import com.kuflow.rest.client.controller.TaskApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SampleRestWorkerLoanConfiguration {

    private final KuFlowRestClient kuFlowRestClient;

    public SampleRestWorkerLoanConfiguration(SampleRestWorkerLoanProperties loanProperties) {
        com.kuflow.rest.client.KuFlowRestClientProperties properties = new com.kuflow.rest.client.KuFlowRestClientProperties();
        properties.setEndpoint(loanProperties.getEndpoint());
        properties.setApplicationId(loanProperties.getApplicationId());
        properties.setToken(loanProperties.getToken());

        this.kuFlowRestClient = new KuFlowRestClient(properties);
    }

    @Bean
    public AuthenticationApi kuflowRestClientAuthenticationApi() {
        return this.kuFlowRestClient.getAuthenticationApi();
    }

    @Bean
    public ProcessApi kuflowRestClientProcessApi() {
        return this.kuFlowRestClient.getProcessApi();
    }

    @Bean
    public TaskApi kuflowRestClientTaskApi() {
        return this.kuFlowRestClient.getTaskApi();
    }
}
