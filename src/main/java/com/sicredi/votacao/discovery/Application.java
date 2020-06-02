/*
 * @(#) Application.java      1.00    30/05/2020
 * Copyrights (c) 2020 Sicredi.
 * Todos os direitos reservados.
 */
package com.sicredi.votacao.discovery;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.amazonaws.services.cloudsearchdomain.AmazonCloudSearchDomainClient;
import com.amazonaws.services.cloudsearchdomain.model.ContentType;
import com.amazonaws.services.cloudsearchdomain.model.UploadDocumentsRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.util.StringInputStream;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Classe principal consumidora de mensagens do Amazon SQS.
 * 
 * @author Sidarta Silva (semprebono@gmail.com)
 * @version $Revision$
 */
@SpringBootApplication
public class Application {

	private static final Logger LOG = LoggerFactory.getLogger(Application.class);

	private static final String BATCH_OPERATION_TEMPLATE = "[{\"type\": \"add\", \"id\": \"%s\", \"fields\": %s}]";

	private static final int PARALLEL_WORKER_THREADS = 10;

	private static final int LONG_POLLING_INTERVAL_SECONDS = 10;

	private static final String QUEUE_NAME = "published_resultado_votacao_queue";

	public static void main(String[] args) {
		ConfigurableApplicationContext ctx = SpringApplication.run(Application.class, args);

		AmazonSQS sqs = ctx.getBean(AmazonSQS.class);
		AmazonCloudSearchDomainClient cloudSearchDomainClient = ctx.getBean(AmazonCloudSearchDomainClient.class);
		ExecutorService executorService = Executors.newFixedThreadPool(PARALLEL_WORKER_THREADS);

		final String queueUrl = sqs.getQueueUrl(QUEUE_NAME).getQueueUrl();

		executorService.submit(() -> {
			while (true) {
				consumeMessagesFromQueue(queueUrl, sqs, cloudSearchDomainClient);
			}
		});
	}

	/**
	 * Método responsável por consumir as mensagens da fila e postar documentos para o domínio de busca do CloudSearch.
	 * @param queueUrl a URL da fila.
	 * @param sqs o objeto {@link AmazonSQS}
	 * @param cloudSearchDomainClient o objeto {@link AmazonCloudSearchDomainClient}.
	 */
	private static void consumeMessagesFromQueue(final String queueUrl, final AmazonSQS sqs,
			final AmazonCloudSearchDomainClient cloudSearchDomainClient) {
		ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest().withQueueUrl(queueUrl)
				.withWaitTimeSeconds(LONG_POLLING_INTERVAL_SECONDS);
		List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
		LOG.info("Mensagens recebidas: {}", messages.size());

		messages.forEach(message -> {
			try {
				LOG.info("Mensagem: {}", message.getBody());
				ObjectMapper mapper = new ObjectMapper();
				TypeReference<HashMap<String, String>> typeRef = new TypeReference<HashMap<String, String>>() {
				};
				Map<String, String> map = mapper.readValue(message.getBody(), typeRef);
				final String sessaoID = map.get("sessao_id");

				final String documentsContent = String.format(BATCH_OPERATION_TEMPLATE, sessaoID, message.getBody());
				LOG.info("Batch: {}", documentsContent);
				UploadDocumentsRequest request = new UploadDocumentsRequest();
				request.setContentType(ContentType.Applicationjson);
				request.setDocuments(new StringInputStream(documentsContent));
				request.setContentLength((long) documentsContent.getBytes().length);

				cloudSearchDomainClient.uploadDocuments(request);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			sqs.deleteMessage(new DeleteMessageRequest(queueUrl, message.getReceiptHandle()));
		});
	}

}
