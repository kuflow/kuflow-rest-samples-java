/*
 * Copyright (c) 2021-present KuFlow S.L.
 *
 * All rights reserved.
 */

package com.kuflow.rest.samples.worker.loan;

import com.kuflow.rest.client.controller.ProcessApi;
import com.kuflow.rest.client.controller.TaskApi;
import com.kuflow.rest.client.net.Webhook;
import com.kuflow.rest.client.resource.AssignTaskCommandResource;
import com.kuflow.rest.client.resource.ProcessResource;
import com.kuflow.rest.client.resource.ProcessStateResource;
import com.kuflow.rest.client.resource.TaskElementValueWrapperResource;
import com.kuflow.rest.client.resource.TaskResource;
import com.kuflow.rest.client.resource.TaskStateResource;
import com.kuflow.rest.client.resource.TasksDefinitionSummaryResource;
import com.kuflow.rest.client.resource.WebhookEventProcessStateChangedDataResource;
import com.kuflow.rest.client.resource.WebhookEventProcessStateChangedResource;
import com.kuflow.rest.client.resource.WebhookEventResource;
import com.kuflow.rest.client.resource.WebhookEventTaskStateChangedDataResource;
import com.kuflow.rest.client.resource.WebhookEventTaskStateChangedResource;
import com.kuflow.rest.samples.worker.loan.util.CastUtils;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/webhooks")
public class SampleRestWorkerLoanController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SampleRestWorkerLoanController.class);

    private static final String TASK_LOAN_APPLICATION = "LOAN_APPLICATION";

    private static final String TASK_APPROVE_LOAN = "APPROVE_LOAN";

    private static final String TASK_NOTIFICATION_REJECTION = "NOTIFICATION_REJECTION";

    private static final String TASK_NOTIFICATION_GRANTED = "NOTIFICATION_GRANTED";

    private final RestTemplate restTemplate;

    private final ProcessApi processApi;

    private final TaskApi taskApi;

    public SampleRestWorkerLoanController(RestTemplateBuilder restTemplateBuilder, ProcessApi processApi, TaskApi taskApi) {
        this.restTemplate = restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(500)).setReadTimeout(Duration.ofSeconds(500)).build();
        this.processApi = processApi;
        this.taskApi = taskApi;
    }

    @PostMapping
    public void handleEvent(@RequestBody String payload) {
        LOGGER.info("Event {}", payload);

        WebhookEventResource event = Webhook.constructEvent(payload);

        switch (event.getType()) {
            case PROCESS__STATE_CHANGED -> this.handleEventProcessStateChanged(CastUtils.cast(event));
            case TASK__STATE_CHANGED -> this.handleEventTaskStateChanged(CastUtils.cast(event));
        }
    }

    private void handleEventProcessStateChanged(WebhookEventProcessStateChangedResource event) {
        WebhookEventProcessStateChangedDataResource data = event.getData();
        if (ProcessStateResource.RUNNING.equals(data.getProcessState())) {
            this.createTaskLoanApplication(data);
        }
    }

    private void handleEventTaskStateChanged(WebhookEventTaskStateChangedResource event) {
        WebhookEventTaskStateChangedDataResource data = event.getData();
        if (data.getTaskCode().equals(TASK_LOAN_APPLICATION) && TaskStateResource.COMPLETED.equals(data.getTaskState())) {
            this.handleTaskLoanApplication(data);
        }
        if (data.getTaskCode().equals(TASK_APPROVE_LOAN) && TaskStateResource.COMPLETED.equals(data.getTaskState())) {
            this.handleTaskApproveLoan(data);
        }
    }

    private void handleTaskApproveLoan(WebhookEventTaskStateChangedDataResource data) {
        TaskResource taskApproveLoan = this.taskApi.retrieveTask(data.getTaskId());

        String authorizedField = taskApproveLoan.getElementValues().get("authorized").getValueAsString();

        TaskResource taskNotification;
        if (authorizedField.equals("OK")) {
            taskNotification = this.createTaskNotificationGranted(data);
        } else {
            taskNotification = this.createTaskNotificationRejection(data);
        }

        ProcessResource process = this.processApi.retrieveProcess(data.getProcessId());

        this.assignTaskToProcessInitiator(taskNotification, process);

        this.processApi.actionsCompleteProcess(data.getProcessId());
    }

    private void handleTaskLoanApplication(WebhookEventTaskStateChangedDataResource data) {
        TaskResource taskLoanApplication = this.taskApi.retrieveTask(data.getTaskId());

        String currencyField = taskLoanApplication.getElementValues().get("currency").getValueAsString();
        String amountField = taskLoanApplication.getElementValues().get("amount").getValueAsString();

        BigDecimal amountEUR = this.convertToEuros(currencyField, amountField);

        if (amountEUR.compareTo(BigDecimal.valueOf(5000)) > 0) {
            this.createTaskApproveLoan(taskLoanApplication, amountEUR);
        } else {
            TaskResource taskNotification = this.createTaskNotificationGranted(data);

            ProcessResource process = this.processApi.retrieveProcess(data.getProcessId());

            this.assignTaskToProcessInitiator(taskNotification, process);

            this.processApi.actionsCompleteProcess(data.getProcessId());
        }
    }

    private void createTaskLoanApplication(WebhookEventProcessStateChangedDataResource data) {
        TasksDefinitionSummaryResource tasksDefinition = new TasksDefinitionSummaryResource();
        tasksDefinition.setCode(TASK_LOAN_APPLICATION);

        TaskResource task = new TaskResource();
        task.setProcessId(data.getProcessId());
        task.setTaskDefinition(tasksDefinition);

        this.taskApi.createTask(task);
    }

    private void createTaskApproveLoan(TaskResource taskLoanApplication, BigDecimal amountEUR) {
        String firstName = taskLoanApplication.getElementValues().get("firstName").getValueAsString();
        String lastName = taskLoanApplication.getElementValues().get("lastName").getValueAsString();

        TasksDefinitionSummaryResource tasksDefinition = new TasksDefinitionSummaryResource();
        tasksDefinition.setCode(TASK_APPROVE_LOAN);

        TaskResource taskApproveLoan = new TaskResource();
        taskApproveLoan.setProcessId(taskLoanApplication.getProcessId());
        taskApproveLoan.setTaskDefinition(tasksDefinition);
        taskApproveLoan.putElementValuesItem("name", TaskElementValueWrapperResource.of(firstName + " " + lastName));
        taskApproveLoan.putElementValuesItem("amountRequested", TaskElementValueWrapperResource.of(amountEUR.toPlainString()));

        this.taskApi.createTask(taskApproveLoan);
    }

    private TaskResource createTaskNotificationRejection(WebhookEventTaskStateChangedDataResource data) {
        TasksDefinitionSummaryResource tasksDefinition = new TasksDefinitionSummaryResource();
        tasksDefinition.setCode(TASK_NOTIFICATION_REJECTION);

        TaskResource taskNotificationRejection = new TaskResource();
        taskNotificationRejection.setProcessId(data.getProcessId());
        taskNotificationRejection.setTaskDefinition(tasksDefinition);

        return this.taskApi.createTask(taskNotificationRejection);
    }

    private TaskResource createTaskNotificationGranted(WebhookEventTaskStateChangedDataResource data) {
        TasksDefinitionSummaryResource tasksDefinition = new TasksDefinitionSummaryResource();
        tasksDefinition.setCode(TASK_NOTIFICATION_GRANTED);

        TaskResource taskNotificationGranted = new TaskResource();
        taskNotificationGranted.setProcessId(data.getProcessId());
        taskNotificationGranted.setTaskDefinition(tasksDefinition);

        return this.taskApi.createTask(taskNotificationGranted);
    }

    private void assignTaskToProcessInitiator(TaskResource taskNotification, ProcessResource process) {
        AssignTaskCommandResource command = new AssignTaskCommandResource();
        command.setPrincipalId(process.getInitiator().getId());

        this.taskApi.actionsAssignTask(taskNotification.getId(), command);
    }

    private BigDecimal convertToEuros(String currencyField, String amountField) {
        BigDecimal amountEUR = new BigDecimal(amountField != null ? amountField : "0");
        if (currencyField.equals("EUR")) {
            return amountEUR;
        } else {
            return this.convert(amountEUR, currencyField, "EUR");
        }
    }

    private BigDecimal convert(BigDecimal amount, String from, String to) {
        String fromTransformed = this.transformCurrencyCode(from);
        String toTransformed = this.transformCurrencyCode(to);
        String endpoint = String.format(
            "https://cdn.jsdelivr.net/gh/fawazahmed0/currency-api@1/latest/currencies/%s/%s.json",
            fromTransformed,
            toTransformed
        );

        ParameterizedTypeReference<HashMap<String, Object>> responseType = new ParameterizedTypeReference<>() {};
        RequestEntity<Void> request = RequestEntity.get(endpoint).build();
        HashMap<String, Object> response = this.restTemplate.exchange(request, responseType).getBody();

        Double conversion = (Double) response.get(toTransformed);

        return amount.multiply(BigDecimal.valueOf(conversion));
    }

    private String transformCurrencyCode(String currency) {
        return switch (currency) {
            case "EUR" -> "eur";
            case "USD" -> "usd";
            case "GBP" -> "gbp";
            default -> throw new RuntimeException("Unsupported currency " + currency);
        };
    }
}
