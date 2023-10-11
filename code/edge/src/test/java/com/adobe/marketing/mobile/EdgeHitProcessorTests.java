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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.util.MapUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class EdgeHitProcessorTests {

	// for Endpoint tests
	public static final String ENDPOINT_PROD_INTERACT = buildEndpoint(
		EdgeNetworkService.RequestType.INTERACT,
		DEFAULT_DOMAIN,
		REQUEST_URL_PROD_PATH,
		null,
		null
	);
	public static final String ENDPOINT_PRE_PROD_INTERACT = buildEndpoint(
		EdgeNetworkService.RequestType.INTERACT,
		DEFAULT_DOMAIN,
		REQUEST_URL_PRE_PROD_PATH,
		null,
		null
	);
	public static final String ENDPOINT_INT_INTERACT = buildEndpoint(
		EdgeNetworkService.RequestType.INTERACT,
		REQUEST_DOMAIN_INT,
		REQUEST_URL_PROD_PATH,
		null,
		null
	);

	public static final String ENDPOINT_PROD_CONSENT = buildEndpoint(
		EdgeNetworkService.RequestType.CONSENT,
		DEFAULT_DOMAIN,
		REQUEST_URL_PROD_PATH,
		null,
		null
	);
	public static final String ENDPOINT_PRE_PROD_CONSENT = buildEndpoint(
		EdgeNetworkService.RequestType.CONSENT,
		DEFAULT_DOMAIN,
		REQUEST_URL_PRE_PROD_PATH,
		null,
		null
	);
	public static final String ENDPOINT_INT_CONSENT = buildEndpoint(
		EdgeNetworkService.RequestType.CONSENT,
		REQUEST_DOMAIN_INT,
		REQUEST_URL_PROD_PATH,
		null,
		null
	);

	public static final String CUSTOM_DOMAIN = "my.awesome.site";
	public static final String ENDPOINT_PROD_CUSTOM_INTERACT = buildEndpoint(
		EdgeNetworkService.RequestType.INTERACT,
		CUSTOM_DOMAIN,
		REQUEST_URL_PROD_PATH,
		null,
		null
	);
	public static final String ENDPOINT_PRE_PROD_CUSTOM_INTERACT = buildEndpoint(
		EdgeNetworkService.RequestType.INTERACT,
		CUSTOM_DOMAIN,
		REQUEST_URL_PRE_PROD_PATH,
		null,
		null
	);

	public static final String ENDPOINT_PROD_CUSTOM_CONSENT = buildEndpoint(
		EdgeNetworkService.RequestType.CONSENT,
		CUSTOM_DOMAIN,
		REQUEST_URL_PROD_PATH,
		null,
		null
	);
	public static final String ENDPOINT_PRE_PROD_CUSTOM_CONSENT = buildEndpoint(
		EdgeNetworkService.RequestType.CONSENT,
		CUSTOM_DOMAIN,
		REQUEST_URL_PRE_PROD_PATH,
		null,
		null
	);

	// Endpoint with location hint
	public static final String LOC_HINT = "lh1";
	public static final String OVERWRITE_PATH = "/va/v1/sessionstart";
	public static final String ENDPOINT_PROD_LOC_HINT_INTERACT = buildEndpoint(
		EdgeNetworkService.RequestType.INTERACT,
		DEFAULT_DOMAIN,
		REQUEST_URL_PROD_PATH,
		null,
		LOC_HINT
	);
	public static final String ENDPOINT_PRE_PROD_LOC_HINT_INTERACT = buildEndpoint(
		EdgeNetworkService.RequestType.INTERACT,
		DEFAULT_DOMAIN,
		REQUEST_URL_PRE_PROD_PATH,
		null,
		LOC_HINT
	);
	public static final String ENDPOINT_INT_LOC_HINT_INTERACT = buildEndpoint(
		EdgeNetworkService.RequestType.INTERACT,
		REQUEST_DOMAIN_INT,
		REQUEST_URL_PROD_PATH,
		null,
		LOC_HINT
	);
	public static final String ENDPOINT_PROD_CUSTOM_LOC_HINT_INTERACT = buildEndpoint(
		EdgeNetworkService.RequestType.INTERACT,
		CUSTOM_DOMAIN,
		REQUEST_URL_PROD_PATH,
		null,
		LOC_HINT
	);
	public static final String ENDPOINT_PRE_PROD_CUSTOM_LOC_HINT_INTERACT = buildEndpoint(
		EdgeNetworkService.RequestType.INTERACT,
		CUSTOM_DOMAIN,
		REQUEST_URL_PRE_PROD_PATH,
		null,
		LOC_HINT
	);

	public static final String ENDPOINT_PROD_LOC_HINT_CONSENT = buildEndpoint(
		EdgeNetworkService.RequestType.CONSENT,
		DEFAULT_DOMAIN,
		REQUEST_URL_PROD_PATH,
		null,
		LOC_HINT
	);
	public static final String ENDPOINT_PRE_PROD_LOC_HINT_CONSENT = buildEndpoint(
		EdgeNetworkService.RequestType.CONSENT,
		DEFAULT_DOMAIN,
		REQUEST_URL_PRE_PROD_PATH,
		null,
		LOC_HINT
	);
	public static final String ENDPOINT_INT_LOC_HINT_CONSENT = buildEndpoint(
		EdgeNetworkService.RequestType.CONSENT,
		REQUEST_DOMAIN_INT,
		REQUEST_URL_PROD_PATH,
		null,
		LOC_HINT
	);
	public static final String ENDPOINT_PROD_CUSTOM_LOC_HINT_CONSENT = buildEndpoint(
		EdgeNetworkService.RequestType.CONSENT,
		CUSTOM_DOMAIN,
		REQUEST_URL_PROD_PATH,
		null,
		LOC_HINT
	);
	public static final String ENDPOINT_PRE_PROD_CUSTOM_LOC_HINT_CONSENT = buildEndpoint(
		EdgeNetworkService.RequestType.CONSENT,
		CUSTOM_DOMAIN,
		REQUEST_URL_PRE_PROD_PATH,
		null,
		LOC_HINT
	);

	public static final String ENDPOINT_PROD_OVERWRITE_PATH_INTERACT = buildEndpoint(
		EdgeNetworkService.RequestType.INTERACT,
		DEFAULT_DOMAIN,
		REQUEST_URL_PROD_PATH,
		OVERWRITE_PATH,
		LOC_HINT
	);
	public static final String ENDPOINT_PRE_PROD_OVERWRITE_PATH_INTERACT = buildEndpoint(
		EdgeNetworkService.RequestType.INTERACT,
		DEFAULT_DOMAIN,
		REQUEST_URL_PRE_PROD_PATH,
		OVERWRITE_PATH,
		LOC_HINT
	);
	public static final String ENDPOINT_INT_OVERWRITE_PATH_INTERACT = buildEndpoint(
		EdgeNetworkService.RequestType.INTERACT,
		REQUEST_DOMAIN_INT,
		REQUEST_URL_PROD_PATH,
		OVERWRITE_PATH,
		LOC_HINT
	);
	public static final String ENDPOINT_INT_CUSTOM_OVERWRITE_PATH_INTERACT = buildEndpoint(
		EdgeNetworkService.RequestType.INTERACT,
		REQUEST_DOMAIN_INT,
		REQUEST_URL_PROD_PATH,
		OVERWRITE_PATH,
		LOC_HINT
	);
	public static final String ENDPOINT_PROD_CUSTOM_OVERWRITE_PATH_INTERACT = buildEndpoint(
		EdgeNetworkService.RequestType.INTERACT,
		CUSTOM_DOMAIN,
		REQUEST_URL_PROD_PATH,
		OVERWRITE_PATH,
		LOC_HINT
	);
	public static final String ENDPOINT_PRE_PROD_CUSTOM_OVERWRITE_PATH_INTERACT = buildEndpoint(
		EdgeNetworkService.RequestType.INTERACT,
		CUSTOM_DOMAIN,
		REQUEST_URL_PRE_PROD_PATH,
		OVERWRITE_PATH,
		LOC_HINT
	);

	// Build request endpoint URL string based on domain, path, and location hint
	private static String buildEndpoint(
		final EdgeNetworkService.RequestType requestType,
		final String domain,
		final String path,
		final String overwritePath,
		final String hint
	) {
		return String.format(
			"https://%s%s%s%s",
			domain,
			path,
			hint != null ? "/" + hint : "",
			(overwritePath != null && !overwritePath.isEmpty()) ? overwritePath : "/v1/" + requestType.type
		);
	}

	private EdgeHitProcessor hitProcessor;

	// Location hint value returned by EdgeStateCallback
	private String locationHitResult = null;

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
	private CountDownLatch latchOfOne;

	private static MockedStatic<CompletionCallbacksManager> callbacksManagersMockedStatic;

	@Mock
	EdgeNetworkService mockEdgeNetworkService;

	@Mock
	CompletionCallbacksManager mockResponseCallbackHandler;

	@Mock
	NetworkResponseHandler mockNetworkResponseHandler;

	@Mock
	NamedCollection mockNamedCollection;

	@Before
	public void setup() throws Exception {
		callbacksManagersMockedStatic = mockStatic(CompletionCallbacksManager.class);
		callbacksManagersMockedStatic
			.when(CompletionCallbacksManager::getInstance)
			.thenReturn(mockResponseCallbackHandler);

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
		latchOfOne = new CountDownLatch(1);
	}

	@After
	public void tearDown() {
		callbacksManagersMockedStatic.close();
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
				public SharedStateResult getSharedState(final String stateOwner, final Event event) {
					if (EdgeConstants.SharedState.ASSURANCE.equals(stateOwner)) {
						assertNull(event); // assert always fetches latest assurance shared state
						return new SharedStateResult(SharedStateStatus.SET, assuranceSharedState);
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
		final Event mockEvent1 = new Event.Builder("mock event 1", "testType", "testSource")
			.setUniqueIdentifier("event1")
			.build();
		final Event mockEvent2 = new Event.Builder("mock event 2", "testType", "testSource")
			.setUniqueIdentifier("event2")
			.build();

		// setup
		final String configId = "456";
		final JSONObject requestBody = getOneEventJson();
		final EdgeEndpoint endpoint = new EdgeEndpoint(
			EdgeNetworkService.RequestType.INTERACT,
			"prod",
			null,
			null,
			null
		);
		final EdgeHit hit = new EdgeHit(configId, requestBody, endpoint);
		when(mockEdgeNetworkService.buildUrl(endpoint, configId, hit.getRequestId())).thenReturn("https://test.com");
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.Retry.NO));

		// test
		doCallRealMethod().when(mockNetworkResponseHandler).processResponseOnComplete(anyString());
		when(mockNetworkResponseHandler.removeWaitingEvents(hit.getRequestId()))
			.thenReturn(
				new ArrayList<Event>() {
					{
						add(mockEvent1);
						add(mockEvent2);
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
			ArgumentMatchers.anyMap(),
			callbackArgCaptor.capture()
		);
		callbackArgCaptor.getValue().onComplete();
		verify(mockNetworkResponseHandler);
		mockNetworkResponseHandler.removeWaitingEvents(hit.getRequestId());
		verify(mockResponseCallbackHandler, times(1));
		mockResponseCallbackHandler.unregisterCallback(mockEvent1.getUniqueIdentifier());
		verify(mockResponseCallbackHandler, times(1));
		mockResponseCallbackHandler.unregisterCallback(mockEvent2.getUniqueIdentifier());
	}

	@Test
	public void testSendNetworkRequest_doesNotCrash_whenOnCompleteCalled_andRemoveWaitingEventsReturnsNull() {
		// setup
		final String configId = "456";
		final JSONObject requestBody = getOneEventJson();
		final EdgeEndpoint endpoint = new EdgeEndpoint(
			EdgeNetworkService.RequestType.INTERACT,
			"prod",
			null,
			null,
			null
		);
		final EdgeHit hit = new EdgeHit(configId, requestBody, endpoint);
		when(mockEdgeNetworkService.buildUrl(endpoint, configId, hit.getRequestId())).thenReturn("https://test.com");
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.Retry.NO));

		// test
		doCallRealMethod().when(mockNetworkResponseHandler).processResponseOnComplete(anyString());
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
			ArgumentMatchers.anyMap(),
			callbackArgCaptor.capture()
		);
		callbackArgCaptor.getValue().onComplete(); // simulates this is done by the doRequest method
		verify(mockNetworkResponseHandler);
		mockNetworkResponseHandler.removeWaitingEvents(hit.getRequestId());
		verify(mockResponseCallbackHandler, never());
		mockResponseCallbackHandler.unregisterCallback(anyString());
	}

	@Test
	public void testSendNetworkRequest_buildsConsentRequest_whenRequestTypeConsent() throws InterruptedException {
		final Event mockEvent1 = new Event.Builder("mock event 1", "testType", "testSource")
			.setUniqueIdentifier("event1")
			.build();

		// setup
		final String configId = "456";
		final JSONObject requestBody = getConsentPayloadJson();
		final EdgeEndpoint endpoint = new EdgeEndpoint(
			EdgeNetworkService.RequestType.CONSENT,
			"prod",
			null,
			null,
			null
		);
		final EdgeHit hit = new EdgeHit(configId, requestBody, endpoint);
		when(mockEdgeNetworkService.buildUrl(endpoint, configId, hit.getRequestId())).thenReturn("https://test.com");
		doCallRealMethod().when(mockNetworkResponseHandler).processResponseOnComplete(anyString());
		when(mockNetworkResponseHandler.removeWaitingEvents(hit.getRequestId()))
			.thenReturn(
				new ArrayList<Event>() {
					{
						add(mockEvent1);
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
				ArgumentMatchers.anyMap(),
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
			ArgumentMatchers.anyMap(),
			callbackArgCaptor.capture()
		);
		callbackArgCaptor.getValue().onComplete();
		verify(mockNetworkResponseHandler);
		mockNetworkResponseHandler.removeWaitingEvents(hit.getRequestId());
		verify(mockResponseCallbackHandler, times(1));
		mockResponseCallbackHandler.unregisterCallback(mockEvent1.getUniqueIdentifier());
	}

	@Test
	public void testProcessHit_onResetHit_clearsStatePayloads() throws InterruptedException {
		// setup
		final Event resetEvent = new Event.Builder("Reset Event", EventType.EDGE_IDENTITY, EventSource.RESET_COMPLETE)
			.build();
		final DataEntity dataEntity = new EdgeDataEntity(resetEvent).toDataEntity();
		assertNotNull(dataEntity);

		// test
		hitProcessor.processHit(
			dataEntity,
			result -> {
				assertTrue(result);
				latchOfOne.countDown();
			}
		);

		try {
			latchOfOne.await(200, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			fail("No HitProcessingResult was received for hitProcessor.processHit");
		}

		// verify state store cleared
		verify(mockNamedCollection, times(1)).remove(EdgeConstants.DataStoreKeys.STORE_PAYLOADS);
	}

	// Test void processHit(@NonNull final DataEntity dataEntity, @NonNull final HitProcessingResult processingResult)
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
		EdgeDataEntity entity = new EdgeDataEntity(getExperienceEvent(), edgeConfig, identityMap);

		// test
		assertProcessHit(entity, true, true);
	}

	@Test
	public void testProcessHit_consentUpdateEvent_happy_sendsNetworkRequest_returnsTrue() {
		// setup
		EdgeDataEntity entity = new EdgeDataEntity(getConsentEvent(), edgeConfig, identityMap);

		// test
		assertProcessHit(entity, true, true);
	}

	@Test
	public void testProcessHit_experienceEvent_sendsNetworkRequest_retryResponse_returnsFalse() {
		// setup
		EdgeDataEntity entity = new EdgeDataEntity(getExperienceEvent(), edgeConfig, identityMap);

		// test
		assertProcessHit(entity, new RetryResult(EdgeNetworkService.Retry.YES), false);
	}

	@Test
	public void testProcessHit_consentUpdateEvent_sendsNetworkRequest__retryResponse_returnsFalse() {
		// setup
		EdgeDataEntity entity = new EdgeDataEntity(getConsentEvent(), edgeConfig, identityMap);

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
	public void testProcessHit_experienceEvent_noEdgeConfigId_doesNotSendNetworkRequest_returnsTrue() {
		// Tests that when a good hit is processed that a network request is made and the request returns 200

		// setup
		EdgeDataEntity entity = new EdgeDataEntity(getExperienceEvent(), null, identityMap);

		// test
		assertProcessHit(entity, false, true);
	}

	@Test
	public void testProcessHit_consentUpdateEvent_noEdgeConfigId_doesNotSendNetworkRequest_returnsTrue() {
		// setup
		EdgeDataEntity entity = new EdgeDataEntity(getConsentEvent(), null, identityMap);

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
		Map<String, String> expectedHeaders = new HashMap<>();
		expectedHeaders.put(EdgeConstants.NetworkKeys.HEADER_KEY_AEP_VALIDATION_TOKEN, "abc|123");

		EdgeDataEntity entity = new EdgeDataEntity(getExperienceEvent(), edgeConfig, identityMap);

		// test
		assertProcessHit(entity, true, expectedHeaders, true);
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
		EdgeDataEntity entity = new EdgeDataEntity(getExperienceEvent(), edgeConfig, identityMap);
		hitProcessor =
			new EdgeHitProcessor(mockNetworkResponseHandler, mockEdgeNetworkService, mockNamedCollection, null, null);

		// test
		assertProcessHit(entity, true, null, true);
	}

	@Test
	public void testProcessHit_assuranceDisabled_doesNotSendAssuranceHeader() {
		// setup
		assuranceSharedState = null;
		EdgeDataEntity entity = new EdgeDataEntity(getExperienceEvent(), edgeConfig, identityMap);

		// test
		assertProcessHit(entity, true, null, true);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentProd_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(getExperienceEvent(), "prod", null, ENDPOINT_PROD_INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentProd_withLocationHint_buildUrlWithProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(getExperienceEvent(), "prod", null, ENDPOINT_PROD_LOC_HINT_INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentProd_withOverwritePath_buildUrlWithProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			getExperienceEventWithOverwritePath("/va/v1/sessionstart"),
			"prod",
			null,
			ENDPOINT_PROD_OVERWRITE_PATH_INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentProd_withNullOverwritePath_doesNotOverwritePath()
		throws Exception {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			getExperienceEventWithOverwritePath(null),
			"prod",
			null,
			ENDPOINT_PROD_LOC_HINT_INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentProd_withEmptyOverwritePath_doesNotOverwritePath() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			getExperienceEventWithOverwritePath(""),
			"prod",
			null,
			ENDPOINT_PROD_LOC_HINT_INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentProd_withOverwritePathContainingDoubleSlashes_doesNotOverwritePath() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			getExperienceEventWithOverwritePath("//va/v1/sessionstart"),
			"prod",
			null,
			ENDPOINT_PROD_LOC_HINT_INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentProd_withOverwritePathContainingQueryParameter_doesNotOverwritePath() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			getExperienceEventWithOverwritePath("/va/v1/sessionstart?query=value"),
			"prod",
			null,
			ENDPOINT_PROD_LOC_HINT_INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentProd_withOverwritePathContainingInvalidCharacter_doesNotOverwritePath() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			getExperienceEventWithOverwritePath("/va/v1/sessionstart/@test"),
			"prod",
			null,
			ENDPOINT_PROD_LOC_HINT_INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentProdAndCustomDomain_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(getExperienceEvent(), "prod", CUSTOM_DOMAIN, ENDPOINT_PROD_CUSTOM_INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentProdAndCustomDomain_withLocationHint_buildUrlWithProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(getExperienceEvent(), "prod", CUSTOM_DOMAIN, ENDPOINT_PROD_CUSTOM_LOC_HINT_INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentProdAndCustomDomain_withOverwritePath_buildUrlWithProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			getExperienceEventWithOverwritePath("/va/v1/sessionstart"),
			"prod",
			CUSTOM_DOMAIN,
			ENDPOINT_PROD_CUSTOM_OVERWRITE_PATH_INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentPreProd_buildUrlWithPreProdEndpoint() {
		assertProcessHitEndpoint(getExperienceEvent(), "pre-prod", null, ENDPOINT_PRE_PROD_INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentPreProd_withLocationHint_buildUrlWithPreProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(getExperienceEvent(), "pre-prod", null, ENDPOINT_PRE_PROD_LOC_HINT_INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentPreProd_withOverwritePath_buildUrlWithProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			getExperienceEventWithOverwritePath("/va/v1/sessionstart"),
			"pre-prod",
			null,
			ENDPOINT_PRE_PROD_OVERWRITE_PATH_INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentPreProdAndCustomDomain_buildUrlWithPreProdEndpoint() {
		assertProcessHitEndpoint(getExperienceEvent(), "pre-prod", CUSTOM_DOMAIN, ENDPOINT_PRE_PROD_CUSTOM_INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentPreProdAndCustomDomain_withLocationHint_buildUrlWithPreProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			getExperienceEvent(),
			"pre-prod",
			CUSTOM_DOMAIN,
			ENDPOINT_PRE_PROD_CUSTOM_LOC_HINT_INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentPreProdAndCustomDomain_withOverwritePath_buildUrlWithProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			getExperienceEventWithOverwritePath("/va/v1/sessionstart"),
			"pre-prod",
			CUSTOM_DOMAIN,
			ENDPOINT_PRE_PROD_CUSTOM_OVERWRITE_PATH_INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentInt_buildUrlWithIntEndpoint() {
		assertProcessHitEndpoint(getExperienceEvent(), "int", null, ENDPOINT_INT_INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentInt_withLocationHint_buildUrlWithIntEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(getExperienceEvent(), "int", null, ENDPOINT_INT_LOC_HINT_INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentInt_withOverwritePath_buildUrlWithIntEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			getExperienceEventWithOverwritePath("/va/v1/sessionstart"),
			"int",
			null,
			ENDPOINT_INT_OVERWRITE_PATH_INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentIntAndCustomDomain_buildUrlWithIntEndpoint() {
		// Integration endpoint does not support custom domains
		assertProcessHitEndpoint(getExperienceEvent(), "int", CUSTOM_DOMAIN, ENDPOINT_INT_INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentIntAndCustomDomain_withLocationHint_buildUrlWithIntEndpoint() {
		locationHitResult = LOC_HINT;
		// Integration endpoint does not support custom domains
		assertProcessHitEndpoint(getExperienceEvent(), "int", CUSTOM_DOMAIN, ENDPOINT_INT_LOC_HINT_INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentIntAndCustomDomain_withOverwritePath_buildUrlWithIntEndpoint() {
		locationHitResult = LOC_HINT;
		// Integration endpoint does not support custom domains
		assertProcessHitEndpoint(
			getExperienceEventWithOverwritePath("/va/v1/sessionstart"),
			"int",
			CUSTOM_DOMAIN,
			ENDPOINT_INT_OVERWRITE_PATH_INTERACT
		);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentEmpty_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(getExperienceEvent(), "", null, ENDPOINT_PROD_INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentNull_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(getExperienceEvent(), null, null, ENDPOINT_PROD_INTERACT);
	}

	@Test
	public void testProcessHit_experienceEvent_edgeEnvironmentInvalid_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(getExperienceEvent(), "somevalue", null, ENDPOINT_PROD_INTERACT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentProd_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(getConsentEvent(), "prod", null, ENDPOINT_PROD_CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentProd_withLocationHint_buildUrlWithProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(getConsentEvent(), "prod", null, ENDPOINT_PROD_LOC_HINT_CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentProd_withOverwritePath_doesNotOverwriteThePath() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			getConsentEventWithOverwritePath(OVERWRITE_PATH),
			"prod",
			null,
			ENDPOINT_PROD_LOC_HINT_CONSENT
		);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentProdAndCustomDomain_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(getConsentEvent(), "prod", CUSTOM_DOMAIN, ENDPOINT_PROD_CUSTOM_CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentProdAndCustomDomain_withLocationHint_buildUrlWithProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(getConsentEvent(), "prod", CUSTOM_DOMAIN, ENDPOINT_PROD_CUSTOM_LOC_HINT_CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentPreProd_buildUrlWithPreProdEndpoint() {
		assertProcessHitEndpoint(getConsentEvent(), "pre-prod", null, ENDPOINT_PRE_PROD_CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentPreProd_withLocationHint_buildUrlWithPreProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(getConsentEvent(), "pre-prod", null, ENDPOINT_PRE_PROD_LOC_HINT_CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentPreProdAndCustomDomain_buildUrlWithPreProdEndpoint() {
		assertProcessHitEndpoint(getConsentEvent(), "pre-prod", CUSTOM_DOMAIN, ENDPOINT_PRE_PROD_CUSTOM_CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentPreProdAndCustomDomain_withLocationHint_buildUrlWithPreProdEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(
			getConsentEvent(),
			"pre-prod",
			CUSTOM_DOMAIN,
			ENDPOINT_PRE_PROD_CUSTOM_LOC_HINT_CONSENT
		);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentInt_buildUrlWithIntEndpoint() {
		assertProcessHitEndpoint(getConsentEvent(), "int", null, ENDPOINT_INT_CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentInt_withLocationHint_buildUrlWithIntEndpoint() {
		locationHitResult = LOC_HINT;
		assertProcessHitEndpoint(getConsentEvent(), "int", null, ENDPOINT_INT_LOC_HINT_CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentIntAndCustomDomain_buildUrlWithIntEndpoint() {
		// Integration endpoint does not support custom domains
		assertProcessHitEndpoint(getConsentEvent(), "int", CUSTOM_DOMAIN, ENDPOINT_INT_CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentIntAndCustomDomain_withLocationHint_buildUrlWithIntEndpoint() {
		locationHitResult = LOC_HINT;
		// Integration endpoint does not support custom domains
		assertProcessHitEndpoint(getConsentEvent(), "int", CUSTOM_DOMAIN, ENDPOINT_INT_LOC_HINT_CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentEmpty_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(getConsentEvent(), "", null, ENDPOINT_PROD_CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentNull_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(getConsentEvent(), null, null, ENDPOINT_PROD_CONSENT);
	}

	@Test
	public void testProcessHit_consentEvent_edgeEnvironmentInvalid_buildUrlWithProdEndpoint() {
		assertProcessHitEndpoint(getConsentEvent(), "somevalue", null, ENDPOINT_PROD_CONSENT);
	}

	@Test
	public void testProcessHit_setsRetryInterval() {
		final int retryInterval = 10;
		mockNetworkServiceResponse("https://test.com", new RetryResult(EdgeNetworkService.Retry.YES, retryInterval));

		// test & verify
		DataEntity dataEntity = new EdgeDataEntity(getExperienceEvent(), edgeConfig, identityMap).toDataEntity();
		assertNotNull(dataEntity);

		assertProcessHitResult(dataEntity, false); // retry is YES so processHit should return false

		// verify retry interval was set
		assertEquals(retryInterval, hitProcessor.retryInterval(dataEntity));

		// Now send hit again but return NO retry and success result
		mockNetworkServiceResponse("https://test.com", new RetryResult(EdgeNetworkService.Retry.NO));

		assertProcessHitResult(dataEntity, true); // retry is NO so processHit should return true

		// verify retry interval was removed
		assertEquals(EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS, hitProcessor.retryInterval(dataEntity));
	}

	@Test
	public void testProcessHit_experienceEvent_sendsImplementationDetails() throws Exception {
		mockNetworkServiceResponse("https://test.com", new RetryResult(EdgeNetworkService.Retry.NO));

		DataEntity dataEntity = new EdgeDataEntity(getExperienceEvent(), edgeConfig, identityMap).toDataEntity();
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
		assertProcessHitResult(dataEntity, true);

		ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				payloadCaptor.capture(),
				ArgumentMatchers.anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			);

		assertTrue(payloadCaptor.getValue().contains("implementationdetails"));

		JSONObject requestJson = new JSONObject(payloadCaptor.getValue());
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
		mockNetworkServiceResponse("https://test.com", new RetryResult(EdgeNetworkService.Retry.NO));

		DataEntity dataEntity = new EdgeDataEntity(getExperienceEvent(), edgeConfig, identityMap).toDataEntity();
		assertNotNull(dataEntity);

		hitProcessor =
			new EdgeHitProcessor(mockNetworkResponseHandler, mockEdgeNetworkService, mockNamedCollection, null, null);

		// test
		assertProcessHitResult(dataEntity, true);

		ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				payloadCaptor.capture(),
				ArgumentMatchers.anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			);

		assertFalse(payloadCaptor.getValue().contains("implementationdetails"));
	}

	@Test
	public void testProcessHit_consentEvent_doesNotSendImplementationDetails() {
		mockNetworkServiceResponse("https://test.com", new RetryResult(EdgeNetworkService.Retry.NO));
		DataEntity dataEntity = new EdgeDataEntity(getConsentEvent(), edgeConfig, identityMap).toDataEntity();
		assertNotNull(dataEntity);

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

		// test
		hitProcessor.processHit(
			dataEntity,
			success -> {
				assertTrue(success);
				latchOfOne.countDown();
			}
		);

		try {
			latchOfOne.await(200, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			fail("No HitProcessingResult was received for hitProcessor.processHit");
		}

		ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				payloadCaptor.capture(),
				ArgumentMatchers.anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			);

		assertFalse(payloadCaptor.getValue().contains("implementationdetails"));
	}

	@Test
	public void testProcessHit_experienceEvent_withDatastreamIdOverrideSet_sendsNetworkRequest_returnsTrue() {
		// Tests that when a good hit is processed that a network request is made and the request returns 200

		// setup
		EdgeDataEntity entity = new EdgeDataEntity(
			getExperienceEventWithConfig("testDatastreamId", null),
			edgeConfig,
			identityMap
		);

		// test
		assertProcessHit(entity, true, true);
	}

	@Test
	public void testProcessHit_experienceEvent_withDatastreamConfigOverrideSet_sendsNetworkRequest_returnsTrue() {
		// Tests that when a good hit is processed that a network request is made and the request returns 200
		Map<String, Object> configOverrides = new HashMap<>();
		configOverrides.put("key", "value");

		// setup
		EdgeDataEntity entity = new EdgeDataEntity(
			getExperienceEventWithConfig(null, configOverrides),
			edgeConfig,
			identityMap
		);

		// test
		assertProcessHit(entity, true, true);
	}

	@Test
	public void testProcessHit_experienceEvent_withDatastreamIdAndConfigOverrideSet_sendsNetworkRequest_returnsTrue() {
		// Tests that when a good hit is processed that a network request is made and the request returns 200
		Map<String, Object> configOverrides = new HashMap<>();
		configOverrides.put("key", "value");

		// setup
		EdgeDataEntity entity = new EdgeDataEntity(
			getExperienceEventWithConfig("testDatastreamId", configOverrides),
			edgeConfig,
			identityMap
		);

		// test
		assertProcessHit(entity, true, true);
	}

	@Test
	public void testProcessHit_experienceEvent_whenDatastreamIdOverrideSet_sendsSdkConfig() throws Exception {
		mockNetworkServiceResponse("https://test.com", new RetryResult(EdgeNetworkService.Retry.NO));

		Map<String, Object> configOverrides = new HashMap<>();
		configOverrides.put("key", "value");

		DataEntity dataEntity = new EdgeDataEntity(
			getExperienceEventWithConfig("testDatastreamId", null),
			edgeConfig,
			identityMap
		)
			.toDataEntity();
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
		assertProcessHitResult(dataEntity, true);

		ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				urlCaptor.capture(),
				payloadCaptor.capture(),
				ArgumentMatchers.anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			);

		// Assert that the url contains the overridden datastreamId
		assertTrue(urlCaptor.getValue().contains("configId=testDatastreamId"));

		assertTrue(payloadCaptor.getValue().contains("sdkConfig"));

		JSONObject requestJson = new JSONObject(payloadCaptor.getValue());
		assertNotNull(requestJson);
		JSONObject metaJson = requestJson.getJSONObject("meta");
		assertNotNull(metaJson);
		JSONObject sdkConfigJson = metaJson.getJSONObject("sdkConfig");
		assertNotNull(sdkConfigJson);
		JSONObject datastreamJson = sdkConfigJson.getJSONObject("datastream");
		assertNotNull(datastreamJson);

		assertEquals("works", datastreamJson.get("original"));
	}

	@Test
	public void testProcessHit_experienceEvent_whenDatastreamConfigOverridesSet_sendsConfigOverrides()
		throws Exception {
		mockNetworkServiceResponse("https://test.com", new RetryResult(EdgeNetworkService.Retry.NO));

		Map<String, Object> configOverrides = new HashMap<>();
		configOverrides.put("key", "value");

		DataEntity dataEntity = new EdgeDataEntity(
			getExperienceEventWithConfig(null, configOverrides),
			edgeConfig,
			identityMap
		)
			.toDataEntity();
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
		assertProcessHitResult(dataEntity, true);

		ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				payloadCaptor.capture(),
				ArgumentMatchers.anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			);

		assertTrue(payloadCaptor.getValue().contains("configOverrides"));

		JSONObject requestJson = new JSONObject(payloadCaptor.getValue());
		assertNotNull(requestJson);
		JSONObject metaJson = requestJson.getJSONObject("meta");
		assertNotNull(metaJson);
		JSONObject configOverridesJson = metaJson.getJSONObject("configOverrides");
		assertNotNull(configOverridesJson);

		assertEquals("value", configOverridesJson.get("key"));
	}

	@Test
	public void testProcessHit_experienceEvent_whenDatastreamIdAndConfigOverridesSet_sendsSdkConfigAndConfigOverrides()
		throws Exception {
		mockNetworkServiceResponse("https://test.com", new RetryResult(EdgeNetworkService.Retry.NO));

		Map<String, Object> configOverrides = new HashMap<>();
		configOverrides.put("key", "value");

		DataEntity dataEntity = new EdgeDataEntity(
			getExperienceEventWithConfig("testDatastreamId", configOverrides),
			edgeConfig,
			identityMap
		)
			.toDataEntity();
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
		assertProcessHitResult(dataEntity, true);

		ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				urlCaptor.capture(),
				payloadCaptor.capture(),
				ArgumentMatchers.anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			);

		assertTrue(payloadCaptor.getValue().contains("configOverrides"));

		JSONObject requestJson = new JSONObject(payloadCaptor.getValue());
		assertNotNull(requestJson);
		JSONObject metaJson = requestJson.getJSONObject("meta");
		assertNotNull(metaJson);

		// Assert sdkConfig

		// Assert that the url contains the overridden datastreamId
		assertTrue(urlCaptor.getValue().contains("configId=testDatastreamId"));

		JSONObject sdkConfigJson = metaJson.getJSONObject("sdkConfig");
		assertNotNull(sdkConfigJson);
		JSONObject datastreamJson = sdkConfigJson.getJSONObject("datastream");
		assertNotNull(datastreamJson);

		assertEquals("works", datastreamJson.get("original"));

		// Assert configOverrides
		JSONObject configOverridesJson = metaJson.getJSONObject("configOverrides");
		assertNotNull(configOverridesJson);

		assertEquals("value", configOverridesJson.get("key"));
	}

	//************************************************** Utils **************************************************

	void assertProcessHitResult(@NotNull final DataEntity entity, final boolean expectedHitProcessingResult) {
		hitProcessor.processHit(
			entity,
			success -> {
				assertEquals(expectedHitProcessingResult, success);
				latchOfOne.countDown();
			}
		);

		try {
			latchOfOne.await(200, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			fail("No HitProcessingResult was received for hitProcessor.processHit");
		}
	}

	void assertProcessHit(
		@NotNull final EdgeDataEntity entity,
		final boolean sendsNetworkRequest,
		final Map<String, String> withHeaders,
		final boolean returns
	) {
		assertProcessHit(
			entity,
			sendsNetworkRequest ? new RetryResult(EdgeNetworkService.Retry.NO) : null,
			withHeaders,
			returns
		);
	}

	void assertProcessHit(final EdgeDataEntity entity, final boolean sendsNetworkRequest, final boolean returns) {
		assertProcessHit(entity, sendsNetworkRequest ? new RetryResult(EdgeNetworkService.Retry.NO) : null, returns);
	}

	void assertProcessHit(final EdgeDataEntity entity, final RetryResult networkResult, final boolean returns) {
		assertProcessHit(entity, networkResult, null, returns);
	}

	void assertProcessHit(
		final EdgeDataEntity entity,
		final RetryResult networkResult,
		final Map<String, String> withHeaders,
		final boolean returns
	) {
		if (networkResult != null) {
			mockNetworkServiceResponse("https://test.com", networkResult);
		}

		// test & verify
		DataEntity dataEntity = entity.toDataEntity();
		assertNotNull(dataEntity);
		hitProcessor.processHit(
			dataEntity,
			success -> {
				assertEquals(
					String.format("Expected processHit to return %s, but it was %s", returns, success),
					returns,
					success
				);
				latchOfOne.countDown();
			}
		);

		try {
			latchOfOne.await(200, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			fail("No HitProcessingResult was received for hitProcessor.processHit");
		}

		if (networkResult != null) {
			ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(HashMap.class);
			ArgumentCaptor<EdgeNetworkService.ResponseCallback> callbackArgCaptor = ArgumentCaptor.forClass(
				EdgeNetworkService.ResponseCallback.class
			);
			verify(mockEdgeNetworkService, times(1))
				.doRequest(anyString(), anyString(), headersCaptor.capture(), callbackArgCaptor.capture());

			if (withHeaders == null || withHeaders.isEmpty()) {
				assertEquals(0, headersCaptor.getValue().size());
			} else {
				assertEquals(withHeaders.size(), headersCaptor.getValue().size());
				assertEquals(withHeaders, headersCaptor.getValue());
			}
			callbackArgCaptor.getValue().onComplete(); // simulates this is done by the doRequest method
		} else {
			verify(mockEdgeNetworkService, never())
				.doRequest(
					anyString(),
					anyString(),
					ArgumentMatchers.anyMap(),
					any(EdgeNetworkService.ResponseCallback.class)
				);
		}
	}

	private void mockNetworkServiceResponse(final String returnBuildUrl, final RetryResult returnRetry) {
		ArgumentCaptor<String> datastreamIdCaptor = ArgumentCaptor.forClass(String.class);

		when(mockEdgeNetworkService.buildUrl(any(EdgeEndpoint.class), datastreamIdCaptor.capture(), anyString()))
			.thenAnswer(
				new Answer<Object>() {
					public Object answer(InvocationOnMock invocation) {
						String capturedDatastreamId = datastreamIdCaptor.getValue();
						return StringUtils.isNullOrEmpty(capturedDatastreamId)
							? returnBuildUrl
							: returnBuildUrl + "?configId=" + capturedDatastreamId;
					}
				}
			);

		//Return(returnBuildUrl);
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(returnRetry);
	}

	private void assertProcessHitEndpoint(
		final Event event,
		final String environment,
		final String domain,
		final String expectedUrl
	) {
		// setup
		edgeConfig = new HashMap<>();
		edgeConfig.put("edge.configId", "works");

		if (environment != null) {
			edgeConfig.put("edge.environment", environment);
		}

		if (domain != null) {
			edgeConfig.put("edge.domain", domain);
		}

		when(mockEdgeNetworkService.buildUrl(any(EdgeEndpoint.class), anyString(), anyString()))
			.thenReturn("https://test.com");

		DataEntity dataEntity = new EdgeDataEntity(event, edgeConfig, identityMap).toDataEntity();
		assertNotNull(dataEntity);

		hitProcessor.processHit(
			dataEntity,
			success -> {
				assertTrue(success);
				latchOfOne.countDown();
			}
		);

		try {
			latchOfOne.await(200, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			fail("No HitProcessingResult was received for hitProcessor.processHit");
		}

		ArgumentCaptor<EdgeEndpoint> envCaptor = ArgumentCaptor.forClass(EdgeEndpoint.class);
		ArgumentCaptor<EdgeNetworkService.RequestType> requestTypeCaptor = ArgumentCaptor.forClass(
			EdgeNetworkService.RequestType.class
		);
		verify(mockEdgeNetworkService, times(1)).buildUrl(envCaptor.capture(), anyString(), anyString());
		assertEquals(expectedUrl, envCaptor.getValue().getEndpoint());
	}

	private JSONObject getOneEventJson() {
		try {
			return new JSONObject(
				"{" +
				"  \"environment\": {" +
				"    \"operatingSystem\": \"iOS\"" +
				"  }," +
				"  \"application\": {" +
				"    \"id\": \"A123\"" +
				"  }," +
				"  \"events\": [" +
				"    {" +
				"      \"xdm\": {" +
				"        \"eventType\": \"commerce.purchases\"," +
				"        \"_id\": \"0db631a2-5017-4502-967b-4fc67e184be6\"," +
				"        \"commerce\": {" +
				"          \"purchases\": {" +
				"            \"value\": 1" +
				"          }," +
				"          \"order\": {" +
				"            \"priceTotal\": 20," +
				"            \"currencyCode\": \"RON\"" +
				"          }" +
				"        }," +
				"        \"timestamp\": \"2020-04-17T13:09:23-07:00\"" +
				"      }" +
				"    }" +
				"  ]" +
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
				"{" +
				"  \"consent\": [" +
				"	{" +
				"    \"standard\": \"Adobe\"," +
				"    \"version\": \"2.0\"," +
				"    \"value\": {" +
				"    	\"collect\": {" +
				"    		\"val\": \"y\"" +
				"  		}," +
				"  	  }" +
				"  	}" +
				"  	]" +
				"}"
			);
		} catch (JSONException e) {
			// nothing
		}

		return null;
	}

	private Event getExperienceEvent() {
		return getExperienceEvent(null, null);
	}

	private Event getExperienceEvent(Map<String, Object> request, Map<String, Object> config) {
		Map<String, Object> xdmData = new HashMap<String, Object>() {
			{
				put("test", "data");
			}
		};
		Map<String, Object> eventData = new HashMap<String, Object>();
		eventData.put("xdm", xdmData);

		if (!MapUtils.isNullOrEmpty(config)) {
			eventData.put("config", config);
		}

		if (!MapUtils.isNullOrEmpty(request)) {
			eventData.put("request", request);
		}

		Event experienceEvent = new Event.Builder("test-experience-event", EventType.EDGE, EventSource.REQUEST_CONTENT)
			.setEventData(eventData)
			.build();
		return experienceEvent;
	}

	private Event getExperienceEventWithConfig(String datastreamIdOverride, Map<String, Object> configOverrides) {
		Map<String, Object> config = new HashMap<>();
		if (!StringUtils.isNullOrEmpty(datastreamIdOverride)) {
			config.put("datastreamIdOverride", datastreamIdOverride);
		}

		if (!MapUtils.isNullOrEmpty(configOverrides)) {
			config.put("datastreamConfigOverride", configOverrides);
		}

		return getExperienceEvent(null, config);
	}

	private Event getConsentEvent() {
		return getConsentEvent(null);
	}

	private Event getConsentEventWithOverwritePath(String path) {
		Map<String, Object> requestMap = new HashMap<>();
		requestMap.put("path", path);
		return getConsentEvent(requestMap);
	}

	private Event getConsentEvent(Map<String, Object> requestMap) {
		Map<String, Object> consentsMap = new HashMap<>();
		Map<String, Object> collectMap = new HashMap<>();
		collectMap.put("val", "y");

		consentsMap.put("collect", collectMap);

		if (!MapUtils.isNullOrEmpty(requestMap)) {
			consentsMap.put("request", requestMap);
		}

		Map<String, Object> finalMap = new HashMap<>();
		finalMap.put("consents", consentsMap);

		Event consentEvent = new Event.Builder("test-consent-event", EventType.EDGE, EventSource.UPDATE_CONSENT)
			.setEventData(finalMap)
			.build();

		return consentEvent;
	}

	private Event getExperienceEventWithOverwritePath(final String path) {
		Map<String, Object> requestMap = new HashMap<>();
		requestMap.put("path", path);

		return getExperienceEvent(requestMap, null);
	}
}
