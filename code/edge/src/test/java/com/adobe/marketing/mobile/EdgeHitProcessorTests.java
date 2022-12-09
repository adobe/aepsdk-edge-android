/*
  Copyright 2021 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile;

import static com.adobe.marketing.mobile.EdgeConstants.NetworkKeys.DEFAULT_DOMAIN;
import static com.adobe.marketing.mobile.EdgeConstants.NetworkKeys.REQUEST_DOMAIN_INT;
import static com.adobe.marketing.mobile.EdgeConstants.NetworkKeys.REQUEST_URL_PRE_PROD_PATH;
import static com.adobe.marketing.mobile.EdgeConstants.NetworkKeys.REQUEST_URL_PROD_PATH;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.NamedCollection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ CompletionCallbacksManager.class, ExtensionApi.class })
public class EdgeHitProcessorTests {

	// for Endpoint tests
	public static final String ENDPOINT_PROD = buildEndpoint(DEFAULT_DOMAIN, REQUEST_URL_PROD_PATH, null);
	public static final String ENDPOINT_PRE_PROD = buildEndpoint(DEFAULT_DOMAIN, REQUEST_URL_PRE_PROD_PATH, null);
	public static final String ENDPOINT_INT = buildEndpoint(REQUEST_DOMAIN_INT, REQUEST_URL_PROD_PATH, null);

	public static final String CUSTOM_DOMAIN = "my.awesome.site";
	public static final String ENDPOINT_PROD_CUSTOM = buildEndpoint(CUSTOM_DOMAIN, REQUEST_URL_PROD_PATH, null);
	public static final String ENDPOINT_PRE_PROD_CUSTOM = buildEndpoint(CUSTOM_DOMAIN, REQUEST_URL_PRE_PROD_PATH, null);

	// Endpoint with location hint
	public static final String LOC_HINT = "lh1";
	public static final String ENDPOINT_PROD_LOC_HINT = buildEndpoint(DEFAULT_DOMAIN, REQUEST_URL_PROD_PATH, LOC_HINT);
	public static final String ENDPOINT_PRE_PROD_LOC_HINT = buildEndpoint(
		DEFAULT_DOMAIN,
		REQUEST_URL_PRE_PROD_PATH,
		LOC_HINT
	);
	public static final String ENDPOINT_INT_LOC_HINT = buildEndpoint(
		REQUEST_DOMAIN_INT,
		REQUEST_URL_PROD_PATH,
		LOC_HINT
	);
	public static final String ENDPOINT_PROD_CUSTOM_LOC_HINT = buildEndpoint(
		CUSTOM_DOMAIN,
		REQUEST_URL_PROD_PATH,
		LOC_HINT
	);
	public static final String ENDPOINT_PRE_PROD_CUSTOM_LOC_HINT = buildEndpoint(
		CUSTOM_DOMAIN,
		REQUEST_URL_PRE_PROD_PATH,
		LOC_HINT
	);

	// Build request endpoint URL string based on domain, path, and location hint
	private static String buildEndpoint(final String domain, final String path, final String hint) {
		return String.format("https://%s%s%s/v1", domain, path, hint != null ? "/" + hint : "");
	}

	private EdgeHitProcessor hitProcessor;

	// Location hint value returned by EdgeStateCallback
	private String locationHitResult = null;

	private final Event experienceEvent = new Event.Builder(
		"test-experience-event",
		EventType.EDGE,
		EventSource.REQUEST_CONTENT
	)
		.setEventData(
			new HashMap<String, Object>() {
				{
					put(
						"xdm",
						new HashMap<String, Object>() {
							{
								put("test", "data");
							}
						}
					);
				}
			}
		)
		.build();

	private final Event consentEvent = new Event.Builder(
		"test-consent-event",
		EventType.EDGE,
		EventSource.UPDATE_CONSENT
	)
		.setEventData(
			new HashMap<String, Object>() {
				{
					put(
						"consents",
						new HashMap<String, Object>() {
							{
								put(
									"collect",
									new HashMap<String, Object>() {
										{
											put("val", "y");
										}
									}
								);
							}
						}
					);
				}
			}
		)
		.build();

	private final Map<String, Object> identityMap = new HashMap<String, Object>() {
		{
			put(
				"identityMap",
				new HashMap<String, Object>() {
					{
						put(
							"ECID",
							new ArrayList<Object>() {
								{
									add(
										new HashMap<String, Object>() {
											{
												put("id", "123");
											}
										}
									);
								}
							}
						);
					}
				}
			);
		}
	};

	private final Map<String, Object> implementationDetails = new HashMap<String, Object>() {
		{
			put(
				"implementationdetails",
				new HashMap<String, Object>() {
					{
						put("version", "3.0.0+1.0.0");
						put("environment", "app");
						put("name", "https://ns.adobe.com/experience/mobilesdk/android");
					}
				}
			);
		}
	};

	private EdgeSharedStateCallback mockSharedStateCallback;
	private Map<String, Object> edgeConfig;
	private Map<String, Object> assuranceSharedState;

	@Mock
	EdgeNetworkService mockEdgeNetworkService;

	@Mock
	CompletionCallbacksManager mockResponseCallbackHandler;

	@Mock
	NetworkResponseHandler mockNetworkResponseHandler;

	@Mock
	RequestBuilder mockRequestBuilder;

	@Mock
	NamedCollection mockNamedCollection;

	@Before
	public void setup() throws Exception {
		mockStatic(CompletionCallbacksManager.class);
		when(CompletionCallbacksManager.getInstance()).thenReturn(mockResponseCallbackHandler);

		whenNew(RequestBuilder.class).withAnyArguments().thenReturn(mockRequestBuilder);
		JSONObject test = new JSONObject();
		test.put("test", "case");
		doReturn(test).when(mockRequestBuilder).getPayloadWithExperienceEvents(isA(List.class));
		doReturn(test).when(mockRequestBuilder).getConsentPayload(any(Event.class));
		setUpDefaultSharedStates();

		hitProcessor =
			new EdgeHitProcessor(
				mockNetworkResponseHandler,
				mockEdgeNetworkService,
				mockNamedCollection,
				mockSharedStateCallback,
				new EdgeStateCallback() {
					@Override
					public Map<String, Object> getImplementationDetails() {
						return implementationDetails;
					}

					@Override
					public String getLocationHint() {
						return locationHitResult;
					}

					@Override
					public void setLocationHint(final String hint, final int ttlSeconds) {
						// not called by EdgeHitProcessor
					}
				}
			);
	}

	private void setUpDefaultSharedStates() {
		// return valid config by default
		edgeConfig =
			new HashMap<String, Object>() {
				{
					put("edge.configId", "works");
				}
			};
		assuranceSharedState = null;
		mockSharedStateCallback =
			new EdgeSharedStateCallback() {
				@Override
				public Map<String, Object> getSharedState(final String stateOwner, final Event event) {
					if (EdgeConstants.SharedState.ASSURANCE.equals(stateOwner)) {
						assertNull(event); // assert always fetches latest assurance shared state
						return assuranceSharedState;
					}

					return null;
				}

				@Override
				public void createSharedState(final Map<String, Object> state, final Event event) {
					// not called by hit processor
				}
			};
	}

	// Test experience events handling based on collect consent value
	@Test
	public void testSendNetworkRequest_executesCleanup_whenOnCompleteCalled() {
		// setup
		final String configId = "456";
		final JSONObject requestBody = getOneEventJson();
		final EdgeEndpoint endpoint = new EdgeEndpoint("prod", null, null);
		final EdgeHit hit = new EdgeHit(configId, requestBody, EdgeNetworkService.RequestType.INTERACT, endpoint);
		when(
			mockEdgeNetworkService.buildUrl(
				EdgeNetworkService.RequestType.INTERACT,
				endpoint,
				configId,
				hit.getRequestId()
			)
		)
			.thenReturn("https://test.com");
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.<String, String>anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.Retry.NO));

		// test
		when(mockNetworkResponseHandler.removeWaitingEvents(hit.getRequestId()))
			.thenReturn(
				new ArrayList<String>() {
					{
						add("event1");
						add("event2");
					}
				}
			);
		hitProcessor.sendNetworkRequest(null, hit, new HashMap<String, String>());

		// verify
		ArgumentCaptor<EdgeNetworkService.ResponseCallback> callbackArgCaptor = ArgumentCaptor.forClass(
			EdgeNetworkService.ResponseCallback.class
		);
		verify(mockEdgeNetworkService);
		mockEdgeNetworkService.doRequest(
			anyString(),
			anyString(),
			ArgumentMatchers.<String, String>anyMap(),
			callbackArgCaptor.capture()
		);
		callbackArgCaptor.getValue().onComplete();
		verify(mockNetworkResponseHandler);
		mockNetworkResponseHandler.removeWaitingEvents(hit.getRequestId());
		verify(mockResponseCallbackHandler, times(1));
		mockResponseCallbackHandler.unregisterCallback("event1");
		verify(mockResponseCallbackHandler, times(1));
		mockResponseCallbackHandler.unregisterCallback("event2");
	}

	@Test
	public void testSendNetworkRequest_doesNotCrash_whenOnCompleteCalled_andRemoveWaitingEventsReturnsNull() {
		// setup
		final String configId = "456";
		final JSONObject requestBody = getOneEventJson();
		final EdgeEndpoint endpoint = new EdgeEndpoint("prod", null, null);
		final EdgeHit hit = new EdgeHit(configId, requestBody, EdgeNetworkService.RequestType.INTERACT, endpoint);
		when(
			mockEdgeNetworkService.buildUrl(
				EdgeNetworkService.RequestType.INTERACT,
				endpoint,
				configId,
				hit.getRequestId()
			)
		)
			.thenReturn("https://test.com");
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.<String, String>anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.Retry.NO));

		// test
		when(mockNetworkResponseHandler.removeWaitingEvents(hit.getRequestId())).thenReturn(null);

		hitProcessor.sendNetworkRequest(null, hit, new HashMap<String, String>());

		// verify
		ArgumentCaptor<EdgeNetworkService.ResponseCallback> callbackArgCaptor = ArgumentCaptor.forClass(
			EdgeNetworkService.ResponseCallback.class
		);
		verify(mockEdgeNetworkService);
		mockEdgeNetworkService.doRequest(
			anyString(),
			anyString(),
			ArgumentMatchers.<String, String>anyMap(),
			callbackArgCaptor.capture()
		);
		callbackArgCaptor.getValue().onComplete(); // simulates this is done by the doRequest method
		verify(mockNetworkResponseHandler);
		mockNetworkResponseHandler.removeWaitingEvents(hit.getRequestId());
		verify(mockResponseCallbackHandler, times(0));
		mockResponseCallbackHandler.unregisterCallback(anyString());
	}

	@Test
	public void testSendNetworkRequest_buildsConsentRequest_whenRequestTypeConsent() throws InterruptedException {
		// setup
		final String configId = "456";
		final JSONObject requestBody = getConsentPayloadJson();
		final EdgeEndpoint endpoint = new EdgeEndpoint("prod", null, null);
		final EdgeHit hit = new EdgeHit(configId, requestBody, EdgeNetworkService.RequestType.CONSENT, endpoint);
		when(
			mockEdgeNetworkService.buildUrl(
				EdgeNetworkService.RequestType.CONSENT,
				endpoint,
				configId,
				hit.getRequestId()
			)
		)
			.thenReturn("https://test.com");
		when(mockNetworkResponseHandler.removeWaitingEvents(hit.getRequestId()))
			.thenReturn(
				new ArrayList<String>() {
					{
						add("event1");
					}
				}
			);
		ArgumentCaptor<EdgeNetworkService.ResponseCallback> callbackArgCaptor = ArgumentCaptor.forClass(
			EdgeNetworkService.ResponseCallback.class
		);

		// test
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.<String, String>anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.Retry.NO, 1));
		hitProcessor.sendNetworkRequest(null, hit, new HashMap<String, String>());

		// verify
		verify(mockEdgeNetworkService, times(1));
		mockEdgeNetworkService.doRequest(
			anyString(),
			anyString(),
			ArgumentMatchers.<String, String>anyMap(),
			callbackArgCaptor.capture()
		);
		callbackArgCaptor.getValue().onComplete();
		verify(mockNetworkResponseHandler);
		mockNetworkResponseHandler.removeWaitingEvents(hit.getRequestId());
		verify(mockResponseCallbackHandler, times(1));
		mockResponseCallbackHandler.unregisterCallback("event1");
	}

	@Test
	public void testProcessHit_onResetHit_clearsStatePayloads() throws InterruptedException {
		// setup
		final Event resetEvent = new Event.Builder("Reset Event", EventType.EDGE_IDENTITY, EventSource.RESET_COMPLETE)
			.build();
		final EdgeDataEntity entity = new EdgeDataEntity(resetEvent);

		// test
		hitProcessor.processHit(new DataEntity(EdgeDataEntitySerializer.serialize(entity)));

		// verify state store cleared
		verify(mockNamedCollection, times(1)).remove(EdgeConstants.DataStoreKeys.STORE_PAYLOADS);
	}

	// Test boolean processHit(final EdgeDataEntity entity)
	@Test
	public void testProcessHit_nullEdgeEntityData_removesHit() {
		// Tests that when `entity` is null, hit is removed from the queue

		// test
		assertProcessHit(null, false, true);
	}

	@Test
	public void testProcessHit_badHit_decodeFails() {
		// Tests that when a `DataEntity` with bad data is passed, that it is not retried and is removed from the queue
		// setup
		// EdgeDataEntity contains event with no data
		EdgeDataEntity entity = new EdgeDataEntity(
			new Event.Builder("testName", "testType", "testSource").build(),
			edgeConfig,
			identityMap
		);

		// test
		assertProcessHit(entity, false, true);
	}

	@Test
	public void testProcessHit_experienceEvent_happy_sendsNetworkRequest_returnsTrue() {
		// Tests that when a good hit is processed that a network request is made and the request returns 200

		// setup
		EdgeDataEntity entity = new EdgeDataEntity(experienceEvent, edgeConfig, identityMap);

		// test
		assertProcessHit(entity, true, true);
	}

	@Test
	public void testProcessHit_consentUpdateEvent_happy_sendsNetworkRequest_returnsTrue() {
		// setup
		EdgeDataEntity entity = new EdgeDataEntity(consentEvent, edgeConfig, identityMap);

		// test
		assertProcessHit(entity, true, true);
	}

	@Test
	public void testProcessHit_experienceEvent_sendsNetworkRequest_retryResponse_returnsFalse() {
		// setup
		EdgeDataEntity entity = new EdgeDataEntity(experienceEvent, edgeConfig, identityMap);

		// test
		assertProcessHit(entity, new RetryResult(EdgeNetworkService.Retry.YES), false);
	}

	@Test
	public void testProcessHit_consentUpdateEvent_sendsNetworkRequest__retryResponse_returnsFalse() {
		// setup
		EdgeDataEntity entity = new EdgeDataEntity(consentEvent, edgeConfig, identityMap);

		// test
		assertProcessHit(entity, new RetryResult(EdgeNetworkService.Retry.YES), false);
	}

	@Test
	public void testProcessHit_consentUpdateEvent_emptyData_doesNotSendNetworkRequest_returnsTrue() {
		// setup
		Event consentEvent = new Event.Builder("test-consent-event", EventType.EDGE, EventSource.UPDATE_CONSENT)
			.build();
		EdgeDataEntity entity = new EdgeDataEntity(consentEvent, edgeConfig, identityMap);

		// test
		assertProcessHit(entity, false, true);
	}

	@Test
	public void testProcessHit_consentUpdateEvent_emptyPayloadDueToInvalidData_doesNotSendNetworkRequest_returnsTrue() {
		// setup
		Event consentEvent = new Event.Builder("test-consent-event", EventType.EDGE, EventSource.UPDATE_CONSENT)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put("some", "consent");
					}
				}
			)
			.build();
		EdgeDataEntity entity = new EdgeDataEntity(consentEvent, edgeConfig, identityMap);

		// test
		assertProcessHit(entity, false, true);
	}

	@Test
	public void testProcessHit_assuranceEnabled_sendsRequestWithAssuranceHeader() {
		// setup
		assuranceSharedState =
			new HashMap<String, Object>() {
				{
					put(EdgeConstants.SharedState.Assurance.INTEGRATION_ID, "abc|123");
				}
			};

		when(
			mockEdgeNetworkService.buildUrl(
				any(EdgeNetworkService.RequestType.class),
				any(EdgeEndpoint.class),
				anyString(),
				anyString()
			)
		)
			.thenReturn("https://test.com");
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.<String, String>anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.Retry.NO));

		EdgeDataEntity entity = new EdgeDataEntity(experienceEvent, edgeConfig, identityMap);

		// test
		boolean success = hitProcessor.processHit(new DataEntity(EdgeDataEntitySerializer.serialize(entity)));

		// verify
		assertTrue(success);

		ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(HashMap.class);
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				anyString(),
				headersCaptor.capture(),
				any(EdgeNetworkService.ResponseCallback.class)
			);
		assertEquals(1, headersCaptor.getValue().size());
		assertEquals(
			"abc|123",
			headersCaptor.getValue().get(EdgeConstants.NetworkKeys.HEADER_KEY_AEP_VALIDATION_TOKEN)
		);
	}

	@Test
	public void testProcessHit_nullSharedStateCallback_doesNotSendAssuranceHeader() {
		// setup
		assuranceSharedState =
			new HashMap<String, Object>() {
				{
					put(EdgeConstants.SharedState.Assurance.INTEGRATION_ID, "abc|123");
				}
			};

		when(
			mockEdgeNetworkService.buildUrl(
				any(EdgeNetworkService.RequestType.class),
				any(EdgeEndpoint.class),
				anyString(),
				anyString()
			)
		)
			.thenReturn("https://test.com");
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.<String, String>anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.Retry.NO));

		EdgeDataEntity entity = new EdgeDataEntity(experienceEvent, edgeConfig, identityMap);

		// test
		hitProcessor =
			new EdgeHitProcessor(mockNetworkResponseHandler, mockEdgeNetworkService, mockNamedCollection, null, null);
		boolean success = hitProcessor.processHit(new DataEntity(EdgeDataEntitySerializer.serialize(entity)));

		// verify
		assertTrue(success);

		ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(HashMap.class);
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				anyString(),
				headersCaptor.capture(),
				any(EdgeNetworkService.ResponseCallback.class)
			);
		assertEquals(0, headersCaptor.getValue().size());
	}

	@Test
	public void testProcessHit_assuranceDisabled_doesNotSendAssuranceHeader() {
		// setup
		assuranceSharedState = null;

		when(
			mockEdgeNetworkService.buildUrl(
				any(EdgeNetworkService.RequestType.class),
				any(EdgeEndpoint.class),
				anyString(),
				anyString()
			)
		)
			.thenReturn("https://test.com");
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.<String, String>anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.Retry.NO));

		EdgeDataEntity entity = new EdgeDataEntity(experienceEvent, edgeConfig, identityMap);

		// test
		boolean success = hitProcessor.processHit(new DataEntity(EdgeDataEntitySerializer.serialize(entity)));

		// verify
		assertTrue(success);

		ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(HashMap.class);
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				anyString(),
				headersCaptor.capture(),
				any(EdgeNetworkService.ResponseCallback.class)
			);
		assertEquals(0, headersCaptor.getValue().size());
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentProd_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(experienceEvent, "prod", null, ENDPOINT_PROD, EdgeNetworkService.RequestType.INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentProd_withLocationHint_buildUrlWithProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			experienceEvent,
			"prod",
			null,
			ENDPOINT_PROD_LOC_HINT,
			EdgeNetworkService.RequestType.INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentProdAndCustomDomain_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(
			experienceEvent,
			"prod",
			CUSTOM_DOMAIN,
			ENDPOINT_PROD_CUSTOM,
			EdgeNetworkService.RequestType.INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentProdAndCustomDomain_withLocationHint_buildUrlWithProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			experienceEvent,
			"prod",
			CUSTOM_DOMAIN,
			ENDPOINT_PROD_CUSTOM_LOC_HINT,
			EdgeNetworkService.RequestType.INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentPreProd_buildUrlWithPreProdEndpoint() {
		assertProcessHitEndpoint(
			experienceEvent,
			"pre-prod",
			null,
			ENDPOINT_PRE_PROD,
			EdgeNetworkService.RequestType.INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentPreProd_withLocationHint_buildUrlWithPreProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			experienceEvent,
			"pre-prod",
			null,
			ENDPOINT_PRE_PROD_LOC_HINT,
			EdgeNetworkService.RequestType.INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentPreProdAndCustomDomain_buildUrlWithPreProdEndpoint() {
		assertProcessHitEndpoint(
			experienceEvent,
			"pre-prod",
			CUSTOM_DOMAIN,
			ENDPOINT_PRE_PROD_CUSTOM,
			EdgeNetworkService.RequestType.INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentPreProdAndCustomDomain_withLocationHint_buildUrlWithPreProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			experienceEvent,
			"pre-prod",
			CUSTOM_DOMAIN,
			ENDPOINT_PRE_PROD_CUSTOM_LOC_HINT,
			EdgeNetworkService.RequestType.INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentInt_buildUrlWithIntEndpoint() {
		assertProcessHitEndpoint(experienceEvent, "int", null, ENDPOINT_INT, EdgeNetworkService.RequestType.INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentInt_withLocationHint_buildUrlWithIntEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			experienceEvent,
			"int",
			null,
			ENDPOINT_INT_LOC_HINT,
			EdgeNetworkService.RequestType.INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentIntAndCustomDomain_buildUrlWithIntEndpoint() {
		// Integration endpoint does not support custom domains
		assertProcessHitEndpoint(
			experienceEvent,
			"int",
			CUSTOM_DOMAIN,
			ENDPOINT_INT,
			EdgeNetworkService.RequestType.INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentIntAndCustomDomain_withLocationHint_buildUrlWithIntEndpoint() {
		locationHitResult = LOC_HINT;
		// Integration endpoint does not support custom domains
		assertProcessHitEndpoint(
			experienceEvent,
			"int",
			CUSTOM_DOMAIN,
			ENDPOINT_INT_LOC_HINT,
			EdgeNetworkService.RequestType.INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentEmpty_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(experienceEvent, "", null, ENDPOINT_PROD, EdgeNetworkService.RequestType.INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentNull_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(experienceEvent, null, null, ENDPOINT_PROD, EdgeNetworkService.RequestType.INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentInvalid_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(
			experienceEvent,
			"somevalue",
			null,
			ENDPOINT_PROD,
			EdgeNetworkService.RequestType.INTERACT
		);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentProd_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(consentEvent, "prod", null, ENDPOINT_PROD, EdgeNetworkService.RequestType.CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentProd_withLocationHint_buildUrlWithProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			consentEvent,
			"prod",
			null,
			ENDPOINT_PROD_LOC_HINT,
			EdgeNetworkService.RequestType.CONSENT
		);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentProdAndCustomDomain_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(
			consentEvent,
			"prod",
			CUSTOM_DOMAIN,
			ENDPOINT_PROD_CUSTOM,
			EdgeNetworkService.RequestType.CONSENT
		);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentProdAndCustomDomain_withLocationHint_buildUrlWithProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			consentEvent,
			"prod",
			CUSTOM_DOMAIN,
			ENDPOINT_PROD_CUSTOM_LOC_HINT,
			EdgeNetworkService.RequestType.CONSENT
		);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentPreProd_buildUrlWithPreProdEndpoint() {
		assertProcessHitEndpoint(
			consentEvent,
			"pre-prod",
			null,
			ENDPOINT_PRE_PROD,
			EdgeNetworkService.RequestType.CONSENT
		);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentPreProd_withLocationHint_buildUrlWithPreProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			consentEvent,
			"pre-prod",
			null,
			ENDPOINT_PRE_PROD_LOC_HINT,
			EdgeNetworkService.RequestType.CONSENT
		);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentPreProdAndCustomDomain_buildUrlWithPreProdEndpoint() {
		assertProcessHitEndpoint(
			consentEvent,
			"pre-prod",
			CUSTOM_DOMAIN,
			ENDPOINT_PRE_PROD_CUSTOM,
			EdgeNetworkService.RequestType.CONSENT
		);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentPreProdAndCustomDomain_withLocationHint_buildUrlWithPreProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			consentEvent,
			"pre-prod",
			CUSTOM_DOMAIN,
			ENDPOINT_PRE_PROD_CUSTOM_LOC_HINT,
			EdgeNetworkService.RequestType.CONSENT
		);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentInt_buildUrlWithIntEndpoint() {
		assertProcessHitEndpoint(consentEvent, "int", null, ENDPOINT_INT, EdgeNetworkService.RequestType.CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentInt_withLocationHint_buildUrlWithIntEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			consentEvent,
			"int",
			null,
			ENDPOINT_INT_LOC_HINT,
			EdgeNetworkService.RequestType.CONSENT
		);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentIntAndCustomDomain_buildUrlWithIntEndpoint() {
		// Integration endpoint does not support custom domains
		assertProcessHitEndpoint(
			consentEvent,
			"int",
			CUSTOM_DOMAIN,
			ENDPOINT_INT,
			EdgeNetworkService.RequestType.CONSENT
		);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentIntAndCustomDomain_withLocationHint_buildUrlWithIntEndpoint() {
		locationHitResult = LOC_HINT;
		// Integration endpoint does not support custom domains
		assertProcessHitEndpoint(
			consentEvent,
			"int",
			CUSTOM_DOMAIN,
			ENDPOINT_INT_LOC_HINT,
			EdgeNetworkService.RequestType.CONSENT
		);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentEmpty_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(consentEvent, "", null, ENDPOINT_PROD, EdgeNetworkService.RequestType.CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentNull_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(consentEvent, null, null, ENDPOINT_PROD, EdgeNetworkService.RequestType.CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentInvalid_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(
			consentEvent,
			"somevalue",
			null,
			ENDPOINT_PROD,
			EdgeNetworkService.RequestType.CONSENT
		);
	}

	@Test
	public void testProcessHit_setsRetryInterval() {
		final int retryInterval = 10;
		when(
			mockEdgeNetworkService.buildUrl(
				any(EdgeNetworkService.RequestType.class),
				any(EdgeEndpoint.class),
				anyString(),
				anyString()
			)
		)
			.thenReturn("https://test.com");
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.<String, String>anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.Retry.YES, retryInterval));

		// test & verify
		EdgeDataEntity entity = new EdgeDataEntity(experienceEvent, edgeConfig, identityMap);
		DataEntity dataEntity = new DataEntity(EdgeDataEntitySerializer.serialize(entity));
		boolean success = hitProcessor.processHit(dataEntity);
		assertFalse(success); // retry is YES so processHit should return false

		// verify retry interval was set
		assertEquals(retryInterval, hitProcessor.retryInterval(dataEntity));

		// Now send hit again but return NO retry and success result
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.<String, String>anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.Retry.NO));

		success = hitProcessor.processHit(dataEntity);
		assertTrue(success); // retry is NO so processHit should return true

		// verify retry interval was removed
		assertEquals(EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS, hitProcessor.retryInterval(dataEntity));
	}

	@Test
	public void testProcessHit_experienceEvent_sendsImplementationDetails() throws Exception {
		when(
			mockEdgeNetworkService.buildUrl(
				any(EdgeNetworkService.RequestType.class),
				any(EdgeEndpoint.class),
				anyString(),
				anyString()
			)
		)
			.thenReturn("https://test.com");
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.<String, String>anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.Retry.NO));

		EdgeDataEntity entity = new EdgeDataEntity(experienceEvent, edgeConfig, identityMap);

		// test
		hitProcessor =
			new EdgeHitProcessor(
				mockNetworkResponseHandler,
				mockEdgeNetworkService,
				mockNamedCollection,
				mockSharedStateCallback,
				new EdgeStateCallback() {
					@Override
					public Map<String, Object> getImplementationDetails() {
						return implementationDetails;
					}

					@Override
					public String getLocationHint() {
						return null;
					}

					@Override
					public void setLocationHint(final String hint, final int ttlSeconds) {
						// not called by hit processor
					}
				}
			);
		boolean success = hitProcessor.processHit(new DataEntity(EdgeDataEntitySerializer.serialize(entity)));

		// verify
		assertTrue(success);

		ArgumentCaptor<String> paylodCaptor = ArgumentCaptor.forClass(String.class);
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				paylodCaptor.capture(),
				ArgumentMatchers.<String, String>anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			);

		assertTrue(paylodCaptor.getValue().contains("implementationdetails"));

		JSONObject requestJson = new JSONObject(paylodCaptor.getValue());
		assertNotNull(requestJson);
		JSONObject xdmJson = requestJson.getJSONObject("xdm");
		assertNotNull(xdmJson);
		JSONObject detailsJson = xdmJson.getJSONObject("implementationdetails");
		assertNotNull(detailsJson);

		assertEquals("3.0.0+1.0.0", detailsJson.get("version"));
		assertEquals("app", detailsJson.get("environment"));
		assertEquals("https://ns.adobe.com/experience/mobilesdk/android", detailsJson.get("name"));
	}

	@Test
	public void testProcessHit_nullStateCallback_doesNotSendImplementationDetails() {
		when(
			mockEdgeNetworkService.buildUrl(
				any(EdgeNetworkService.RequestType.class),
				any(EdgeEndpoint.class),
				anyString(),
				anyString()
			)
		)
			.thenReturn("https://test.com");
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.<String, String>anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.Retry.NO));

		EdgeDataEntity entity = new EdgeDataEntity(experienceEvent, edgeConfig, identityMap);

		// test
		hitProcessor =
			new EdgeHitProcessor(mockNetworkResponseHandler, mockEdgeNetworkService, mockNamedCollection, null, null);
		boolean success = hitProcessor.processHit(new DataEntity(EdgeDataEntitySerializer.serialize(entity)));

		// verify
		assertTrue(success);

		ArgumentCaptor<String> paylodCaptor = ArgumentCaptor.forClass(String.class);
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				paylodCaptor.capture(),
				ArgumentMatchers.<String, String>anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			);

		assertFalse(paylodCaptor.getValue().contains("implementationdetails"));
	}

	@Test
	public void testProcessHit_consentEvent_doesNotSendImplementationDetails() {
		when(
			mockEdgeNetworkService.buildUrl(
				any(EdgeNetworkService.RequestType.class),
				any(EdgeEndpoint.class),
				anyString(),
				anyString()
			)
		)
			.thenReturn("https://test.com");
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.<String, String>anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.Retry.NO));

		EdgeDataEntity entity = new EdgeDataEntity(consentEvent, edgeConfig, identityMap);

		// test
		hitProcessor =
			new EdgeHitProcessor(
				mockNetworkResponseHandler,
				mockEdgeNetworkService,
				mockNamedCollection,
				mockSharedStateCallback,
				new EdgeStateCallback() {
					@Override
					public Map<String, Object> getImplementationDetails() {
						return implementationDetails;
					}

					@Override
					public String getLocationHint() {
						return null;
					}

					@Override
					public void setLocationHint(final String hint, final int ttlSeconds) {
						// not called by hit processor
					}
				}
			);
		boolean success = hitProcessor.processHit(new DataEntity(EdgeDataEntitySerializer.serialize(entity)));

		// verify
		assertTrue(success);

		ArgumentCaptor<String> paylodCaptor = ArgumentCaptor.forClass(String.class);
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				paylodCaptor.capture(),
				ArgumentMatchers.<String, String>anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			);

		assertFalse(paylodCaptor.getValue().contains("implementationdetails"));
	}

	void assertProcessHit(final EdgeDataEntity entity, final boolean sendsNetworkRequest, final boolean returns) {
		assertProcessHit(entity, sendsNetworkRequest ? new RetryResult(EdgeNetworkService.Retry.NO) : null, returns);
	}

	void assertProcessHit(final EdgeDataEntity entity, final RetryResult networkResult, final boolean returns) {
		if (networkResult != null) {
			when(
				mockEdgeNetworkService.buildUrl(
					any(EdgeNetworkService.RequestType.class),
					any(EdgeEndpoint.class),
					anyString(),
					anyString()
				)
			)
				.thenReturn("https://test.com");
			when(
				mockEdgeNetworkService.doRequest(
					anyString(),
					anyString(),
					ArgumentMatchers.<String, String>anyMap(),
					any(EdgeNetworkService.ResponseCallback.class)
				)
			)
				.thenReturn(networkResult);
		}

		// test & verify
		boolean success = hitProcessor.processHit(new DataEntity(EdgeDataEntitySerializer.serialize(entity)));
		assertEquals(
			String.format("Expected processHit to return %s, but it was %s", returns, success),
			returns,
			success
		);

		if (networkResult != null) {
			ArgumentCaptor<EdgeNetworkService.ResponseCallback> callbackArgCaptor = ArgumentCaptor.forClass(
				EdgeNetworkService.ResponseCallback.class
			);
			verify(mockEdgeNetworkService, times(1))
				.doRequest(
					anyString(),
					anyString(),
					ArgumentMatchers.<String, String>anyMap(),
					callbackArgCaptor.capture()
				);
			callbackArgCaptor.getValue().onComplete(); // simulates this is done by the doRequest method
		} else {
			verify(mockEdgeNetworkService, times(0))
				.doRequest(
					anyString(),
					anyString(),
					ArgumentMatchers.<String, String>anyMap(),
					any(EdgeNetworkService.ResponseCallback.class)
				);
		}
	}

	private void assertProcessHitEndpoint(
		final Event event,
		final String environment,
		final String domain,
		final String expectedUrl,
		final EdgeNetworkService.RequestType expectedRequestType
	) {
		// setup
		edgeConfig = new HashMap<String, Object>();
		edgeConfig.put("edge.configId", "works");

		if (environment != null) {
			edgeConfig.put("edge.environment", environment);
		}

		if (domain != null) {
			edgeConfig.put("edge.domain", domain);
		}

		when(
			mockEdgeNetworkService.buildUrl(
				any(EdgeNetworkService.RequestType.class),
				any(EdgeEndpoint.class),
				anyString(),
				anyString()
			)
		)
			.thenReturn("https://test.com");

		EdgeDataEntity entity = new EdgeDataEntity(event, edgeConfig, identityMap);

		// test
		boolean success = hitProcessor.processHit(new DataEntity(EdgeDataEntitySerializer.serialize(entity)));

		// verify
		assertTrue(success);

		ArgumentCaptor<EdgeEndpoint> envCaptor = ArgumentCaptor.forClass(EdgeEndpoint.class);
		ArgumentCaptor<EdgeNetworkService.RequestType> requestTypeCaptor = ArgumentCaptor.forClass(
			EdgeNetworkService.RequestType.class
		);
		verify(mockEdgeNetworkService, times(1))
			.buildUrl(requestTypeCaptor.capture(), envCaptor.capture(), anyString(), anyString());
		assertEquals(expectedUrl, envCaptor.getValue().getEndpoint());
		assertEquals(expectedRequestType, requestTypeCaptor.getValue());
	}

	private JSONObject getOneEventJson() {
		try {
			return new JSONObject(
				"{\n" +
				"  \"environment\": {\n" +
				"    \"operatingSystem\": \"iOS\"\n" +
				"  },\n" +
				"  \"application\": {\n" +
				"    \"id\": \"A123\"\n" +
				"  },\n" +
				"  \"events\": [\n" +
				"    {\n" +
				"      \"xdm\": {\n" +
				"        \"eventType\": \"commerce.purchases\",\n" +
				"        \"_id\": \"0db631a2-5017-4502-967b-4fc67e184be6\",\n" +
				"        \"commerce\": {\n" +
				"          \"purchases\": {\n" +
				"            \"value\": 1\n" +
				"          },\n" +
				"          \"order\": {\n" +
				"            \"priceTotal\": 20,\n" +
				"            \"currencyCode\": \"RON\"\n" +
				"          }\n" +
				"        },\n" +
				"        \"timestamp\": \"2020-04-17T13:09:23-07:00\"\n" +
				"      }\n" +
				"    }\n" +
				"  ]\n" +
				"}"
			);
		} catch (JSONException e) {
			// nothing
		}

		return null;
	}

	private JSONObject getConsentPayloadJson() {
		try {
			return new JSONObject(
				"{\n" +
				"  \"consent\": [\n" +
				"	{\n" +
				"    \"standard\": \"Adobe\",\n" +
				"    \"version\": \"2.0\",\n" +
				"    \"value\": {\n" +
				"    	\"collect\": {\n" +
				"    		\"val\": \"y\"\n" +
				"  		},\n" +
				"  	  }\n" +
				"  	}\n" +
				"  	]\n" +
				"}"
			);
		} catch (JSONException e) {
			// nothing
		}

		return null;
	}
}
