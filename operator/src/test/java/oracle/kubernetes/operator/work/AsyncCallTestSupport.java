// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import static oracle.kubernetes.operator.calls.AsyncRequestStep.RESPONSE_COMPONENT_NAME;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.ApiException;
import java.net.HttpURLConnection;
import java.util.Collections;
import oracle.kubernetes.operator.calls.CallFactory;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.helpers.AsyncRequestStepFactory;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.ResponseStep;

/**
 * Support for writing unit tests that use CallBuilder to send requests that expect asynchronous
 * responses.
 *
 * <p>The setUp should invoke #installRequestStepFactory to modify CallBuilder for unit testing,
 * while capturing the memento to clean up during tearDown.
 *
 * <p>The test must define the simulated responses to the calls it will test, by invoking
 * #createCannedResponse, any qualifiers, and the result of the call. For example:
 *
 * <p>testSupport.createCannedResponse("deleteIngress") .withNamespace(namespace).withName(name)
 * .failingWith(HttpURLConnection.HTTP_CONFLICT);
 *
 * <p>will report a conflict failure on an attempt to delete an Ingress with the specified name and
 * namespace.
 *
 * <p>testSupport.createCannedResponse("listPod") .withNamespace(namespace) .returning(new
 * V1PodList().items(Arrays.asList(pod1, pod2, pod3);
 *
 * <p>will return a list of pods after a query with the specified namespace.
 */
@SuppressWarnings("unused")
public class AsyncCallTestSupport extends FiberTestSupport {

  private static final RequestStepFactoryRouter ROUTER = new RequestStepFactoryRouter();
  private CallTestSupport callTestSupport = new CallTestSupport();

  /**
   * Installs a factory into CallBuilder to use canned responses.
   *
   * @return a memento which can be used to restore the production factory
   */
  public Memento installRequestStepFactory() throws NoSuchFieldException {
    ROUTER.setRequestStepFactory(new RequestStepFactory());
    return StaticStubSupport.install(CallBuilder.class, "STEP_FACTORY", ROUTER);
  }

  // CallBuilder.STEP_FACTORY is final, meaning that the JDK may choose to cache it at some point,
  // and miss its being set, thus using the wrong test factory. To avoid this, we install a constant
  // instance of this class and then set its own non-final field with the actual test step factory.
  private static class RequestStepFactoryRouter implements AsyncRequestStepFactory {
    private transient AsyncRequestStepFactory requestStepFactory;

    void setRequestStepFactory(AsyncRequestStepFactory factory) {
      this.requestStepFactory = factory;
    }

    @Override
    public <T> Step createRequestAsync(
        ResponseStep<T> next,
        RequestParams requestParams,
        CallFactory<T> factory,
        ClientPool helper,
        int timeoutSeconds,
        int maxRetryCount,
        String fieldSelector,
        String labelSelector,
        String resourceVersion) {
      return requestStepFactory.createRequestAsync(
          next,
          requestParams,
          factory,
          helper,
          timeoutSeconds,
          maxRetryCount,
          fieldSelector,
          labelSelector,
          resourceVersion);
    }
  }

  private class RequestStepFactory implements AsyncRequestStepFactory {

    @Override
    public <T> Step createRequestAsync(
        ResponseStep<T> next,
        RequestParams requestParams,
        CallFactory<T> factory,
        ClientPool helper,
        int timeoutSeconds,
        int maxRetryCount,
        String fieldSelector,
        String labelSelector,
        String resourceVersion) {
      return new CannedResponseStep(
          next, callTestSupport.getMatchingResponse(requestParams, fieldSelector, labelSelector));
    }
  }

  /**
   * Primes CallBuilder to expect a request for the specified method.
   *
   * @param forMethod the name of the method
   * @return a canned response which may be qualified by parameters and defines how CallBuilder
   *     should react.
   */
  public CallTestSupport.CannedResponse createCannedResponse(String forMethod) {
    return callTestSupport.createCannedResponse(forMethod);
  }

  /**
   * Throws an exception if any of the defined responses were not invoked during the test. This
   * should generally be called during tearDown().
   */
  public void verifyAllDefinedResponsesInvoked() {
    callTestSupport.verifyAllDefinedResponsesInvoked();
  }

  private static class CannedResponseStep extends Step {
    private CallTestSupport.CannedResponse cannedResponse;

    CannedResponseStep(Step next, CallTestSupport.CannedResponse cannedResponse) {
      super(next);
      this.cannedResponse = cannedResponse;
    }

    @Override
    public NextAction apply(Packet packet) {
      CallTestSupport.CannedResponse cannedResponse = this.cannedResponse;
      CallResponse callResponse = cannedResponse.getCallResponse();
      packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(callResponse));

      return doNext(packet);
    }
  }

  private static class SuccessStep<T> extends Step {
    private final T result;

    SuccessStep(T result, Step next) {
      super(next);
      this.result = result;
    }

    @Override
    public NextAction apply(Packet packet) {
      packet
          .getComponents()
          .put(
              RESPONSE_COMPONENT_NAME,
              Component.createFor(
                  new CallResponse<>(
                      result, null, HttpURLConnection.HTTP_OK, Collections.emptyMap())));

      return doNext(packet);
    }
  }

  private static class FailureStep extends Step {
    private final int status;

    FailureStep(int status, Step next) {
      super(next);
      this.status = status;
    }

    @SuppressWarnings("unchecked")
    @Override
    public NextAction apply(Packet packet) {
      packet
          .getComponents()
          .put(
              RESPONSE_COMPONENT_NAME,
              Component.createFor(
                  new CallResponse(null, new ApiException(), status, Collections.emptyMap())));

      return doNext(packet);
    }
  }
}
