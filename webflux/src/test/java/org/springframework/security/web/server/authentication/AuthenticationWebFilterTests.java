/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.security.web.server.authentication;

import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import reactor.core.publisher.Mono;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.reactive.server.WebTestClientBuilder;
import org.springframework.security.web.server.context.SecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.web.reactive.function.client.ExchangeFilterFunctions.basicAuthentication;
import static org.springframework.web.reactive.function.client.ExchangeFilterFunctions.Credentials.basicAuthenticationCredentials;


/**
 * @author Rob Winch
 * @since 5.0
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationWebFilterTests {
	@Mock
	private AuthenticationSuccessHandler successHandler;
	@Mock
	private Function<ServerWebExchange,Mono<Authentication>> authenticationConverter;
	@Mock
	private ReactiveAuthenticationManager authenticationManager;
	@Mock
	private AuthenticationFailureHandler failureHandler;
	@Mock
	private SecurityContextRepository securityContextRepository;

	private AuthenticationWebFilter filter;

	@Before
	public void setup() {
		this.filter = new AuthenticationWebFilter(this.authenticationManager);
		this.filter.setAuthenticationSuccessHandler(this.successHandler);
		this.filter.setAuthenticationConverter(this.authenticationConverter);
		this.filter.setSecurityContextRepository(this.securityContextRepository);
		this.filter.setAuthenticationFailureHandler(this.failureHandler);
	}

	@Test
	public void filterWhenDefaultsAndNoAuthenticationThenContinues() {
		this.filter = new AuthenticationWebFilter(this.authenticationManager);

		WebTestClient client = WebTestClientBuilder
			.bindToWebFilters(this.filter)
			.build();

		EntityExchangeResult<String> result = client.get()
			.uri("/")
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class).consumeWith(b -> assertThat(b.getResponseBody()).isEqualTo("ok"))
			.returnResult();

		verifyZeroInteractions(this.authenticationManager);
		assertThat(result.getResponseCookies()).isEmpty();
	}

	@Test
	public void filterWhenDefaultsAndAuthenticationSuccessThenContinues() {
		when(this.authenticationManager.authenticate(any())).thenReturn(Mono.just(new TestingAuthenticationToken("test","this", "ROLE")));
		this.filter = new AuthenticationWebFilter(this.authenticationManager);

		WebTestClient client = WebTestClientBuilder
			.bindToWebFilters(this.filter)
			.filter(basicAuthentication())
			.build();

		EntityExchangeResult<String> result = client
			.get()
			.uri("/")
			.attributes(basicAuthenticationCredentials("test", "this"))
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class).consumeWith(b -> assertThat(b.getResponseBody()).isEqualTo("ok"))
			.returnResult();

		assertThat(result.getResponseCookies()).isEmpty();
	}

	@Test
	public void filterWhenDefaultsAndAuthenticationFailThenUnauthorized() {
		when(this.authenticationManager.authenticate(any())).thenReturn(Mono.error(new BadCredentialsException("failed")));
		this.filter = new AuthenticationWebFilter(this.authenticationManager);

		WebTestClient client = WebTestClientBuilder
			.bindToWebFilters(this.filter)
			.filter(basicAuthentication())
			.build();

		EntityExchangeResult<Void> result = client
			.get()
			.uri("/")
			.attributes(basicAuthenticationCredentials("test", "this"))
			.exchange()
			.expectStatus().isUnauthorized()
			.expectHeader().valueMatches("WWW-Authenticate", "Basic realm=\"Realm\"")
			.expectBody().isEmpty();

		assertThat(result.getResponseCookies()).isEmpty();
	}

	@Test
	public void filterWhenConvertEmptyThenOk() {
		when(this.authenticationConverter.apply(any())).thenReturn(Mono.empty());

		WebTestClient client = WebTestClientBuilder
			.bindToWebFilters(this.filter)
			.build();

		client
			.get()
			.uri("/")
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class).consumeWith(b -> assertThat(b.getResponseBody()).isEqualTo("ok"))
			.returnResult();

		verify(this.securityContextRepository, never()).save(any(), any());
		verifyZeroInteractions(this.authenticationManager, this.successHandler,
			this.failureHandler);
	}

	@Test
	public void filterWhenConvertErrorThenServerError() {
		when(this.authenticationConverter.apply(any())).thenReturn(Mono.error(new RuntimeException("Unexpected")));

		WebTestClient client = WebTestClientBuilder
			.bindToWebFilters(this.filter)
			.build();

		client
			.get()
			.uri("/")
			.exchange()
			.expectStatus().is5xxServerError()
			.expectBody().isEmpty();

		verify(this.securityContextRepository, never()).save(any(), any());
		verifyZeroInteractions(this.authenticationManager, this.successHandler,
			this.failureHandler);
	}

	@Test
	public void filterWhenConvertAndAuthenticationSuccessThenSuccess() {
		Mono<Authentication> authentication = Mono.just(new TestingAuthenticationToken("test", "this", "ROLE_USER"));
		when(this.authenticationConverter.apply(any())).thenReturn(authentication);
		when(this.authenticationManager.authenticate(any())).thenReturn(authentication);
		when(this.successHandler.success(any(),any())).thenReturn(Mono.empty());
		when(this.securityContextRepository.save(any(),any())).thenAnswer( a -> Mono.just(a.getArguments()[0]));

		WebTestClient client = WebTestClientBuilder
			.bindToWebFilters(this.filter)
			.build();

		client
			.get()
			.uri("/")
			.exchange()
			.expectStatus().isOk()
			.expectBody().isEmpty();

		verify(this.successHandler).success(eq(authentication.block()), any());
		verify(this.securityContextRepository).save(any(), any());
		verifyZeroInteractions(this.failureHandler);
	}

	@Test
	public void filterWhenNotMatchAndConvertAndAuthenticationSuccessThenContinues() {
		this.filter.setRequiresAuthenticationMatcher(e -> ServerWebExchangeMatcher.MatchResult.notMatch());

		WebTestClient client = WebTestClientBuilder
			.bindToWebFilters(this.filter)
			.filter(basicAuthentication())
			.build();

		EntityExchangeResult<String> result = client
			.get()
			.uri("/")
			.attributes(basicAuthenticationCredentials("test", "this"))
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class).consumeWith(b -> assertThat(b.getResponseBody()).isEqualTo("ok"))
			.returnResult();

		assertThat(result.getResponseCookies()).isEmpty();
		verifyZeroInteractions(this.authenticationConverter, this.authenticationManager, this.successHandler);
	}

	@Test
	public void filterWhenConvertAndAuthenticationFailThenEntryPoint() {
		Mono<Authentication> authentication = Mono.just(new TestingAuthenticationToken("test", "this", "ROLE_USER"));
		when(this.authenticationConverter.apply(any())).thenReturn(authentication);
		when(this.authenticationManager.authenticate(any())).thenReturn(Mono.error(new BadCredentialsException("Failed")));
		when(this.failureHandler.onAuthenticationFailure(any(),any())).thenReturn(Mono.empty());

		WebTestClient client = WebTestClientBuilder
			.bindToWebFilters(this.filter)
			.build();

		client
			.get()
			.uri("/")
			.exchange()
			.expectStatus().isOk()
			.expectBody().isEmpty();

		verify(this.failureHandler).onAuthenticationFailure(any(),any());
		verify(this.securityContextRepository, never()).save(any(), any());
		verifyZeroInteractions(this.successHandler);
	}

	@Test
	public void filterWhenConvertAndAuthenticationExceptionThenServerError() {
		Mono<Authentication> authentication = Mono.just(new TestingAuthenticationToken("test", "this", "ROLE_USER"));
		when(this.authenticationConverter.apply(any())).thenReturn(authentication);
		when(this.authenticationManager.authenticate(any())).thenReturn(Mono.error(new RuntimeException("Failed")));
		when(this.failureHandler.onAuthenticationFailure(any(),any())).thenReturn(Mono.empty());

		WebTestClient client = WebTestClientBuilder
			.bindToWebFilters(this.filter)
			.build();

		client
			.get()
			.uri("/")
			.exchange()
			.expectStatus().is5xxServerError()
			.expectBody().isEmpty();

		verify(this.securityContextRepository, never()).save(any(), any());
		verifyZeroInteractions(this.successHandler, this.failureHandler);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setRequiresAuthenticationMatcherWhenNullThenException() {
		this.filter.setRequiresAuthenticationMatcher(null);
	}
}
