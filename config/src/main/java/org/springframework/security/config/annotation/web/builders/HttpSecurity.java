/*
 * Copyright 2002-2016 the original author or authors.
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
package org.springframework.security.config.annotation.web.builders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;

import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.AbstractConfiguredSecurityBuilder;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.SecurityBuilder;
import org.springframework.security.config.annotation.SecurityConfigurer;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.AbstractRequestMatcherRegistry;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.AnonymousConfigurer;
import org.springframework.security.config.annotation.web.configurers.ChannelSecurityConfigurer;
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.config.annotation.web.configurers.JeeConfigurer;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.config.annotation.web.configurers.PortMapperConfigurer;
import org.springframework.security.config.annotation.web.configurers.RememberMeConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer;
import org.springframework.security.config.annotation.web.configurers.ServletApiConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.annotation.web.configurers.X509Configurer;
import org.springframework.security.config.annotation.web.configurers.openid.OpenIDLoginConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.oauth2.client.web.AuthorizationGrantTokenExchanger;
import org.springframework.security.oauth2.client.web.AuthorizationRequestUriBuilder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.PortMapper;
import org.springframework.security.web.PortMapperImpl;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

/**
 * A {@link HttpSecurity} is similar to Spring Security's XML &lt;http&gt; element in the
 * namespace configuration. It allows configuring web based security for specific http
 * requests. By default it will be applied to all requests, but can be restricted using
 * {@link #requestMatcher(RequestMatcher)} or other similar methods.
 *
 * <h2>Example Usage</h2>
 *
 * The most basic form based configuration can be seen below. The configuration will
 * require that any URL that is requested will require a User with the role "ROLE_USER".
 * It also defines an in memory authentication scheme with a user that has the username
 * "user", the password "password", and the role "ROLE_USER". For additional examples,
 * refer to the Java Doc of individual methods on {@link HttpSecurity}.
 *
 * <pre>
 * &#064;Configuration
 * &#064;EnableWebSecurity
 * public class FormLoginSecurityConfig extends WebSecurityConfigurerAdapter {
 *
 * 	&#064;Override
 * 	protected void configure(HttpSecurity http) throws Exception {
 * 		http.authorizeRequests().antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;).and().formLogin();
 * 	}
 *
 * 	&#064;Override
 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
 * 		auth.inMemoryAuthentication().withUser(&quot;user&quot;).password(&quot;password&quot;).roles(&quot;USER&quot;);
 * 	}
 * }
 * </pre>
 *
 * @author Rob Winch
 * @since 3.2
 * @see EnableWebSecurity
 */
public final class HttpSecurity extends
		AbstractConfiguredSecurityBuilder<DefaultSecurityFilterChain, HttpSecurity>
		implements SecurityBuilder<DefaultSecurityFilterChain>,
		HttpSecurityBuilder<HttpSecurity> {
	private final RequestMatcherConfigurer requestMatcherConfigurer;
	private List<Filter> filters = new ArrayList<Filter>();
	private RequestMatcher requestMatcher = AnyRequestMatcher.INSTANCE;
	private FilterComparator comparator = new FilterComparator();

	/**
	 * Creates a new instance
	 * @param objectPostProcessor the {@link ObjectPostProcessor} that should be used
	 * @param authenticationBuilder the {@link AuthenticationManagerBuilder} to use for
	 * additional updates
	 * @param sharedObjects the shared Objects to initialize the {@link HttpSecurity} with
	 * @see WebSecurityConfiguration
	 */
	@SuppressWarnings("unchecked")
	public HttpSecurity(ObjectPostProcessor<Object> objectPostProcessor,
			AuthenticationManagerBuilder authenticationBuilder,
			Map<Class<? extends Object>, Object> sharedObjects) {
		super(objectPostProcessor);
		Assert.notNull(authenticationBuilder, "authenticationBuilder cannot be null");
		setSharedObject(AuthenticationManagerBuilder.class, authenticationBuilder);
		for (Map.Entry<Class<? extends Object>, Object> entry : sharedObjects
				.entrySet()) {
			setSharedObject((Class<Object>) entry.getKey(), entry.getValue());
		}
		ApplicationContext context = (ApplicationContext) sharedObjects
				.get(ApplicationContext.class);
		this.requestMatcherConfigurer = new RequestMatcherConfigurer(context);
	}

	private ApplicationContext getContext() {
		return getSharedObject(ApplicationContext.class);
	}

	/**
	 * Allows configuring OpenID based authentication.
	 *
	 * <h2>Example Configurations</h2>
	 *
	 * A basic example accepting the defaults and not using attribute exchange:
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class OpenIDLoginConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) {
	 * 		http.authorizeRequests().antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;).and().openidLogin()
	 * 				.permitAll();
	 * 	}
	 *
	 * 	&#064;Override
	 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
	 * 		auth.inMemoryAuthentication()
	 * 				// the username must match the OpenID of the user you are
	 * 				// logging in with
	 * 				.withUser(
	 * 						&quot;https://www.google.com/accounts/o8/id?id=lmkCn9xzPdsxVwG7pjYMuDgNNdASFmobNkcRPaWU&quot;)
	 * 				.password(&quot;password&quot;).roles(&quot;USER&quot;);
	 * 	}
	 * }
	 * </pre>
	 *
	 * A more advanced example demonstrating using attribute exchange and providing a
	 * custom AuthenticationUserDetailsService that will make any user that authenticates
	 * a valid user.
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class OpenIDLoginConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) {
	 * 		http.authorizeRequests()
	 * 				.antMatchers(&quot;/**&quot;)
	 * 				.hasRole(&quot;USER&quot;)
	 * 				.and()
	 * 				.openidLogin()
	 * 				.loginPage(&quot;/login&quot;)
	 * 				.permitAll()
	 * 				.authenticationUserDetailsService(
	 * 						new AutoProvisioningUserDetailsService())
	 * 				.attributeExchange(&quot;https://www.google.com/.*&quot;).attribute(&quot;email&quot;)
	 * 				.type(&quot;http://axschema.org/contact/email&quot;).required(true).and()
	 * 				.attribute(&quot;firstname&quot;).type(&quot;http://axschema.org/namePerson/first&quot;)
	 * 				.required(true).and().attribute(&quot;lastname&quot;)
	 * 				.type(&quot;http://axschema.org/namePerson/last&quot;).required(true).and().and()
	 * 				.attributeExchange(&quot;.*yahoo.com.*&quot;).attribute(&quot;email&quot;)
	 * 				.type(&quot;http://schema.openid.net/contact/email&quot;).required(true).and()
	 * 				.attribute(&quot;fullname&quot;).type(&quot;http://axschema.org/namePerson&quot;)
	 * 				.required(true).and().and().attributeExchange(&quot;.*myopenid.com.*&quot;)
	 * 				.attribute(&quot;email&quot;).type(&quot;http://schema.openid.net/contact/email&quot;)
	 * 				.required(true).and().attribute(&quot;fullname&quot;)
	 * 				.type(&quot;http://schema.openid.net/namePerson&quot;).required(true);
	 * 	}
	 * }
	 *
	 * public class AutoProvisioningUserDetailsService implements
	 * 		AuthenticationUserDetailsService&lt;OpenIDAuthenticationToken&gt; {
	 * 	public UserDetails loadUserDetails(OpenIDAuthenticationToken token)
	 * 			throws UsernameNotFoundException {
	 * 		return new User(token.getName(), &quot;NOTUSED&quot;,
	 * 				AuthorityUtils.createAuthorityList(&quot;ROLE_USER&quot;));
	 * 	}
	 * }
	 * </pre>
	 *
	 * @return the {@link OpenIDLoginConfigurer} for further customizations.
	 *
	 * @throws Exception
	 * @see OpenIDLoginConfigurer
	 */
	public OpenIDLoginConfigurer<HttpSecurity> openidLogin() throws Exception {
		return getOrApply(new OpenIDLoginConfigurer<HttpSecurity>());
	}

	/**
	 * Adds the Security headers to the response. This is activated by default when using
	 * {@link WebSecurityConfigurerAdapter}'s default constructor. Accepting the
	 * default provided by {@link WebSecurityConfigurerAdapter} or only invoking
	 * {@link #headers()} without invoking additional methods on it, is the equivalent of:
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class CsrfSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 *     protected void configure(HttpSecurity http) throws Exception {
	 *         http
	 *             .headers()
	 *                 .contentTypeOptions()
	 *                 .and()
	 *                 .xssProtection()
	 *                 .and()
	 *                 .cacheControl()
	 *                 .and()
	 *                 .httpStrictTransportSecurity()
	 *                 .and()
	 *                 .frameOptions()
	 *                 .and()
	 *             ...;
	 *     }
	 * }
	 * </pre>
	 *
	 * You can disable the headers using the following:
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class CsrfSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 *     protected void configure(HttpSecurity http) throws Exception {
	 *         http
	 *             .headers().disable()
	 *             ...;
	 *     }
	 * }
	 * </pre>
	 *
	 * You can enable only a few of the headers by first invoking
	 * {@link HeadersConfigurer#defaultsDisabled()}
	 * and then invoking the appropriate methods on the {@link #headers()} result.
	 * For example, the following will enable {@link HeadersConfigurer#cacheControl()} and
	 * {@link HeadersConfigurer#frameOptions()} only.
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class CsrfSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 *     protected void configure(HttpSecurity http) throws Exception {
	 *         http
	 *             .headers()
	 *                  .defaultsDisabled()
	 *                  .cacheControl()
	 *                  .and()
	 *                  .frameOptions()
	 *                  .and()
	 *             ...;
	 *     }
	 * }
	 * </pre>
	 *
	 * You can also choose to keep the defaults but explicitly disable a subset of headers.
	 * For example, the following will enable all the default headers except
	 * {@link HeadersConfigurer#frameOptions()}.
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class CsrfSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 *     protected void configure(HttpSecurity http) throws Exception {
	 *         http
	 *             .headers()
	 *                  .frameOptions()
	 *                  	.disable()
	 *                  .and()
	 *             ...;
	 *     }
	 * }
	 * </pre>
	 *
	 * @return
	 * @throws Exception
	 * @see HeadersConfigurer
	 */
	public HeadersConfigurer<HttpSecurity> headers() throws Exception {
		return getOrApply(new HeadersConfigurer<HttpSecurity>());
	}

	/**
	 * Adds a {@link CorsFilter} to be used. If a bean by the name of corsFilter is
	 * provided, that {@link CorsFilter} is used. Else if corsConfigurationSource is
	 * defined, then that {@link CorsConfiguration} is used. Otherwise, if Spring MVC is
	 * on the classpath a {@link HandlerMappingIntrospector} is used.
	 *
	 * @return the {@link CorsConfigurer} for customizations
	 * @throws Exception
	 */
	public CorsConfigurer<HttpSecurity> cors() throws Exception {
		return getOrApply(new CorsConfigurer<HttpSecurity>());
	}

	/**
	 * Allows configuring of Session Management.
	 *
	 * <h2>Example Configuration</h2>
	 *
	 * The following configuration demonstrates how to enforce that only a single instance
	 * of a user is authenticated at a time. If a user authenticates with the username
	 * "user" without logging out and an attempt to authenticate with "user" is made the
	 * first session will be forcibly terminated and sent to the "/login?expired" URL.
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class SessionManagementSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http.authorizeRequests().anyRequest().hasRole(&quot;USER&quot;).and().formLogin()
	 * 				.permitAll().and().sessionManagement().maximumSessions(1)
	 * 				.expiredUrl(&quot;/login?expired&quot;);
	 * 	}
	 *
	 * 	&#064;Override
	 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
	 * 		auth.inMemoryAuthentication().withUser(&quot;user&quot;).password(&quot;password&quot;).roles(&quot;USER&quot;);
	 * 	}
	 * }
	 * </pre>
	 *
	 * When using {@link SessionManagementConfigurer#maximumSessions(int)}, do not forget
	 * to configure {@link HttpSessionEventPublisher} for the application to ensure that
	 * expired sessions are cleaned up.
	 *
	 * In a web.xml this can be configured using the following:
	 *
	 * <pre>
	 * &lt;listener&gt;
	 *      &lt;listener-class&gt;org.springframework.security.web.session.HttpSessionEventPublisher&lt;/listener-class&gt;
	 * &lt;/listener&gt;
	 * </pre>
	 *
	 * Alternatively,
	 * {@link AbstractSecurityWebApplicationInitializer#enableHttpSessionEventPublisher()}
	 * could return true.
	 *
	 * @return the {@link SessionManagementConfigurer} for further customizations
	 * @throws Exception
	 */
	public SessionManagementConfigurer<HttpSecurity> sessionManagement() throws Exception {
		return getOrApply(new SessionManagementConfigurer<HttpSecurity>());
	}

	/**
	 * Allows configuring a {@link PortMapper} that is available from
	 * {@link HttpSecurity#getSharedObject(Class)}. Other provided
	 * {@link SecurityConfigurer} objects use this configured {@link PortMapper} as a
	 * default {@link PortMapper} when redirecting from HTTP to HTTPS or from HTTPS to
	 * HTTP (for example when used in combination with {@link #requiresChannel()}. By
	 * default Spring Security uses a {@link PortMapperImpl} which maps the HTTP port 8080
	 * to the HTTPS port 8443 and the HTTP port of 80 to the HTTPS port of 443.
	 *
	 * <h2>Example Configuration</h2>
	 *
	 * The following configuration will ensure that redirects within Spring Security from
	 * HTTP of a port of 9090 will redirect to HTTPS port of 9443 and the HTTP port of 80
	 * to the HTTPS port of 443.
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class PortMapperSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http.authorizeRequests().antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;).and().formLogin()
	 * 				.permitAll().and()
	 * 				// Example portMapper() configuration
	 * 				.portMapper().http(9090).mapsTo(9443).http(80).mapsTo(443);
	 * 	}
	 *
	 * 	&#064;Override
	 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
	 * 		auth.inMemoryAuthentication().withUser(&quot;user&quot;).password(&quot;password&quot;).roles(&quot;USER&quot;);
	 * 	}
	 * }
	 * </pre>
	 *
	 * @return the {@link PortMapperConfigurer} for further customizations
	 * @throws Exception
	 * @see #requiresChannel()
	 */
	public PortMapperConfigurer<HttpSecurity> portMapper() throws Exception {
		return getOrApply(new PortMapperConfigurer<HttpSecurity>());
	}

	/**
	 * Configures container based pre authentication. In this case, authentication
	 * is managed by the Servlet Container.
	 *
	 * <h2>Example Configuration</h2>
	 *
	 * The following configuration will use the principal found on the
	 * {@link HttpServletRequest} and if the user is in the role "ROLE_USER" or
	 * "ROLE_ADMIN" will add that to the resulting {@link Authentication}.
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class JeeSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http.authorizeRequests().antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;).and()
	 * 		// Example jee() configuration
	 * 				.jee().mappableRoles(&quot;ROLE_USER&quot;, &quot;ROLE_ADMIN&quot;);
	 * 	}
	 * }
	 * </pre>
	 *
	 * Developers wishing to use pre authentication with the container will need to ensure
	 * their web.xml configures the security constraints. For example, the web.xml (there
	 * is no equivalent Java based configuration supported by the Servlet specification)
	 * might look like:
	 *
	 * <pre>
	 * &lt;login-config&gt;
	 *     &lt;auth-method&gt;FORM&lt;/auth-method&gt;
	 *     &lt;form-login-config&gt;
	 *         &lt;form-login-page&gt;/login&lt;/form-login-page&gt;
	 *         &lt;form-error-page&gt;/login?error&lt;/form-error-page&gt;
	 *     &lt;/form-login-config&gt;
	 * &lt;/login-config&gt;
	 *
	 * &lt;security-role&gt;
	 *     &lt;role-name&gt;ROLE_USER&lt;/role-name&gt;
	 * &lt;/security-role&gt;
	 * &lt;security-constraint&gt;
	 *     &lt;web-resource-collection&gt;
	 *     &lt;web-resource-name&gt;Public&lt;/web-resource-name&gt;
	 *         &lt;description&gt;Matches unconstrained pages&lt;/description&gt;
	 *         &lt;url-pattern&gt;/login&lt;/url-pattern&gt;
	 *         &lt;url-pattern&gt;/logout&lt;/url-pattern&gt;
	 *         &lt;url-pattern&gt;/resources/*&lt;/url-pattern&gt;
	 *     &lt;/web-resource-collection&gt;
	 * &lt;/security-constraint&gt;
	 * &lt;security-constraint&gt;
	 *     &lt;web-resource-collection&gt;
	 *         &lt;web-resource-name&gt;Secured Areas&lt;/web-resource-name&gt;
	 *         &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
	 *     &lt;/web-resource-collection&gt;
	 *     &lt;auth-constraint&gt;
	 *         &lt;role-name&gt;ROLE_USER&lt;/role-name&gt;
	 *     &lt;/auth-constraint&gt;
	 * &lt;/security-constraint&gt;
	 * </pre>
	 *
	 * Last you will need to configure your container to contain the user with the correct
	 * roles. This configuration is specific to the Servlet Container, so consult your
	 * Servlet Container's documentation.
	 *
	 * @return the {@link JeeConfigurer} for further customizations
	 * @throws Exception
	 */
	public JeeConfigurer<HttpSecurity> jee() throws Exception {
		return getOrApply(new JeeConfigurer<HttpSecurity>());
	}

	/**
	 * Configures X509 based pre authentication.
	 *
	 * <h2>Example Configuration</h2>
	 *
	 * The following configuration will attempt to extract the username from the X509
	 * certificate. Remember that the Servlet Container will need to be configured to
	 * request client certificates in order for this to work.
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class X509SecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http.authorizeRequests().antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;).and()
	 * 		// Example x509() configuration
	 * 				.x509();
	 * 	}
	 * }
	 * </pre>
	 *
	 * @return the {@link X509Configurer} for further customizations
	 * @throws Exception
	 */
	public X509Configurer<HttpSecurity> x509() throws Exception {
		return getOrApply(new X509Configurer<HttpSecurity>());
	}

	/**
	 * Allows configuring of Remember Me authentication.
	 *
	 * <h2>Example Configuration</h2>
	 *
	 * The following configuration demonstrates how to allow token based remember me
	 * authentication. Upon authenticating if the HTTP parameter named "remember-me"
	 * exists, then the user will be remembered even after their
	 * {@link javax.servlet.http.HttpSession} expires.
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class RememberMeSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
	 * 		auth.inMemoryAuthentication().withUser(&quot;user&quot;).password(&quot;password&quot;).roles(&quot;USER&quot;);
	 * 	}
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http.authorizeRequests().antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;).and().formLogin()
	 * 				.permitAll().and()
	 * 				// Example Remember Me Configuration
	 * 				.rememberMe();
	 * 	}
	 * }
	 * </pre>
	 *
	 * @return the {@link RememberMeConfigurer} for further customizations
	 * @throws Exception
	 */
	public RememberMeConfigurer<HttpSecurity> rememberMe() throws Exception {
		return getOrApply(new RememberMeConfigurer<HttpSecurity>());
	}

	/**
	 * Allows restricting access based upon the {@link HttpServletRequest} using
	 *
	 * <h2>Example Configurations</h2>
	 *
	 * The most basic example is to configure all URLs to require the role "ROLE_USER".
	 * The configuration below requires authentication to every URL and will grant access
	 * to both the user "admin" and "user".
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class AuthorizeUrlsSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http.authorizeRequests().antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;).and().formLogin();
	 * 	}
	 *
	 * 	&#064;Override
	 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
	 * 		auth.inMemoryAuthentication().withUser(&quot;user&quot;).password(&quot;password&quot;).roles(&quot;USER&quot;)
	 * 				.and().withUser(&quot;admin&quot;).password(&quot;password&quot;).roles(&quot;ADMIN&quot;, &quot;USER&quot;);
	 * 	}
	 * }
	 * </pre>
	 *
	 * We can also configure multiple URLs. The configuration below requires
	 * authentication to every URL and will grant access to URLs starting with /admin/ to
	 * only the "admin" user. All other URLs either user can access.
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class AuthorizeUrlsSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http.authorizeRequests().antMatchers(&quot;/admin/**&quot;).hasRole(&quot;ADMIN&quot;)
	 * 				.antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;).and().formLogin();
	 * 	}
	 *
	 * 	&#064;Override
	 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
	 * 		auth.inMemoryAuthentication().withUser(&quot;user&quot;).password(&quot;password&quot;).roles(&quot;USER&quot;)
	 * 				.and().withUser(&quot;admin&quot;).password(&quot;password&quot;).roles(&quot;ADMIN&quot;, &quot;USER&quot;);
	 * 	}
	 * }
	 * </pre>
	 *
	 * Note that the matchers are considered in order. Therefore, the following is invalid
	 * because the first matcher matches every request and will never get to the second
	 * mapping:
	 *
	 * <pre>
	 * http.authorizeRequests().antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;).antMatchers(&quot;/admin/**&quot;)
	 * 		.hasRole(&quot;ADMIN&quot;)
	 * </pre>
	 *
	 * @see #requestMatcher(RequestMatcher)
	 *
	 * @return
	 * @throws Exception
	 */
	public ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry authorizeRequests()
			throws Exception {
		ApplicationContext context = getContext();
		return getOrApply(new ExpressionUrlAuthorizationConfigurer<HttpSecurity>(context))
				.getRegistry();
	}

	/**
	 * Allows configuring the Request Cache. For example, a protected page (/protected)
	 * may be requested prior to authentication. The application will redirect the user to
	 * a login page. After authentication, Spring Security will redirect the user to the
	 * originally requested protected page (/protected). This is automatically applied
	 * when using {@link WebSecurityConfigurerAdapter}.
	 *
	 * @return the {@link RequestCacheConfigurer} for further customizations
	 * @throws Exception
	 */
	public RequestCacheConfigurer<HttpSecurity> requestCache() throws Exception {
		return getOrApply(new RequestCacheConfigurer<HttpSecurity>());
	}

	/**
	 * Allows configuring exception handling. This is automatically applied when using
	 * {@link WebSecurityConfigurerAdapter}.
	 *
	 * @return the {@link ExceptionHandlingConfigurer} for further customizations
	 * @throws Exception
	 */
	public ExceptionHandlingConfigurer<HttpSecurity> exceptionHandling() throws Exception {
		return getOrApply(new ExceptionHandlingConfigurer<HttpSecurity>());
	}

	/**
	 * Sets up management of the {@link SecurityContext} on the
	 * {@link SecurityContextHolder} between {@link HttpServletRequest}'s. This is
	 * automatically applied when using {@link WebSecurityConfigurerAdapter}.
	 *
	 * @return the {@link SecurityContextConfigurer} for further customizations
	 * @throws Exception
	 */
	public SecurityContextConfigurer<HttpSecurity> securityContext() throws Exception {
		return getOrApply(new SecurityContextConfigurer<HttpSecurity>());
	}

	/**
	 * Integrates the {@link HttpServletRequest} methods with the values found on the
	 * {@link SecurityContext}. This is automatically applied when using
	 * {@link WebSecurityConfigurerAdapter}.
	 *
	 * @return the {@link ServletApiConfigurer} for further customizations
	 * @throws Exception
	 */
	public ServletApiConfigurer<HttpSecurity> servletApi() throws Exception {
		return getOrApply(new ServletApiConfigurer<HttpSecurity>());
	}

	/**
	 * Adds CSRF support. This is activated by default when using
	 * {@link WebSecurityConfigurerAdapter}'s default constructor. You can disable it
	 * using:
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class CsrfSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 *     protected void configure(HttpSecurity http) throws Exception {
	 *         http
	 *             .csrf().disable()
	 *             ...;
	 *     }
	 * }
	 * </pre>
	 *
	 * @return the {@link ServletApiConfigurer} for further customizations
	 * @throws Exception
	 */
	public CsrfConfigurer<HttpSecurity> csrf() throws Exception {
		ApplicationContext context = getContext();
		return getOrApply(new CsrfConfigurer<HttpSecurity>(context));
	}

	/**
	 * Provides logout support. This is automatically applied when using
	 * {@link WebSecurityConfigurerAdapter}. The default is that accessing the URL
	 * "/logout" will log the user out by invalidating the HTTP Session, cleaning up any
	 * {@link #rememberMe()} authentication that was configured, clearing the
	 * {@link SecurityContextHolder}, and then redirect to "/login?success".
	 *
	 * <h2>Example Custom Configuration</h2>
	 *
	 * The following customization to log out when the URL "/custom-logout" is invoked.
	 * Log out will remove the cookie named "remove", not invalidate the HttpSession,
	 * clear the SecurityContextHolder, and upon completion redirect to "/logout-success".
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class LogoutSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http.authorizeRequests().antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;).and().formLogin()
	 * 				.and()
	 * 				// sample logout customization
	 * 				.logout().deleteCookies(&quot;remove&quot;).invalidateHttpSession(false)
	 * 				.logoutUrl(&quot;/custom-logout&quot;).logoutSuccessUrl(&quot;/logout-success&quot;);
	 * 	}
	 *
	 * 	&#064;Override
	 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
	 * 		auth.inMemoryAuthentication().withUser(&quot;user&quot;).password(&quot;password&quot;).roles(&quot;USER&quot;);
	 * 	}
	 * }
	 * </pre>
	 *
	 * @return
	 * @throws Exception
	 */
	public LogoutConfigurer<HttpSecurity> logout() throws Exception {
		return getOrApply(new LogoutConfigurer<HttpSecurity>());
	}

	/**
	 * Allows configuring how an anonymous user is represented. This is automatically
	 * applied when used in conjunction with {@link WebSecurityConfigurerAdapter}. By
	 * default anonymous users will be represented with an
	 * {@link org.springframework.security.authentication.AnonymousAuthenticationToken}
	 * and contain the role "ROLE_ANONYMOUS".
	 *
	 * <h2>Example Configuration</h2>
	 *
	 * The following configuration demonstrates how to specify that anonymous users should
	 * contain the role "ROLE_ANON" instead.
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class AnononymousSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http.authorizeRequests().antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;).and().formLogin()
	 * 				.and()
	 * 				// sample anonymous customization
	 * 				.anonymous().authorities(&quot;ROLE_ANON&quot;);
	 * 	}
	 *
	 * 	&#064;Override
	 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
	 * 		auth.inMemoryAuthentication().withUser(&quot;user&quot;).password(&quot;password&quot;).roles(&quot;USER&quot;);
	 * 	}
	 * }
	 * </pre>
	 *
	 * The following demonstrates how to represent anonymous users as null. Note that this
	 * can cause {@link NullPointerException} in code that assumes anonymous
	 * authentication is enabled.
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class AnononymousSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http.authorizeRequests().antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;).and().formLogin()
	 * 				.and()
	 * 				// sample anonymous customization
	 * 				.anonymous().disabled();
	 * 	}
	 *
	 * 	&#064;Override
	 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
	 * 		auth.inMemoryAuthentication().withUser(&quot;user&quot;).password(&quot;password&quot;).roles(&quot;USER&quot;);
	 * 	}
	 * }
	 * </pre>
	 *
	 * @return
	 * @throws Exception
	 */
	public AnonymousConfigurer<HttpSecurity> anonymous() throws Exception {
		return getOrApply(new AnonymousConfigurer<HttpSecurity>());
	}

	/**
	 * Specifies to support form based authentication. If
	 * {@link FormLoginConfigurer#loginPage(String)} is not specified a default login page
	 * will be generated.
	 *
	 * <h2>Example Configurations</h2>
	 *
	 * The most basic configuration defaults to automatically generating a login page at
	 * the URL "/login", redirecting to "/login?error" for authentication failure. The
	 * details of the login page can be found on
	 * {@link FormLoginConfigurer#loginPage(String)}
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class FormLoginSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http.authorizeRequests().antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;).and().formLogin();
	 * 	}
	 *
	 * 	&#064;Override
	 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
	 * 		auth.inMemoryAuthentication().withUser(&quot;user&quot;).password(&quot;password&quot;).roles(&quot;USER&quot;);
	 * 	}
	 * }
	 * </pre>
	 *
	 * The configuration below demonstrates customizing the defaults.
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class FormLoginSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http.authorizeRequests().antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;).and().formLogin()
	 * 				.usernameParameter(&quot;username&quot;) // default is username
	 * 				.passwordParameter(&quot;password&quot;) // default is password
	 * 				.loginPage(&quot;/authentication/login&quot;) // default is /login with an HTTP get
	 * 				.failureUrl(&quot;/authentication/login?failed&quot;) // default is /login?error
	 * 				.loginProcessingUrl(&quot;/authentication/login/process&quot;); // default is /login
	 * 																		// with an HTTP
	 * 																		// post
	 * 	}
	 *
	 * 	&#064;Override
	 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
	 * 		auth.inMemoryAuthentication().withUser(&quot;user&quot;).password(&quot;password&quot;).roles(&quot;USER&quot;);
	 * 	}
	 * }
	 * </pre>
	 *
	 * @see FormLoginConfigurer#loginPage(String)
	 *
	 * @return
	 * @throws Exception
	 */
	public FormLoginConfigurer<HttpSecurity> formLogin() throws Exception {
		return getOrApply(new FormLoginConfigurer<HttpSecurity>());
	}

	/**
	 * Configures authentication against an external <i>OAuth 2.0</i> or <i>OpenID Connect 1.0</i> Provider.
	 * <br>
	 * <br>
	 *
	 * The <i>&quot;authentication flow&quot;</i> is realized using the <b>Authorization Code Grant</b>,
	 * as specified in the <a target="_blank" href="https://tools.ietf.org/html/rfc6749#section-4.1">OAuth 2.0 Authorization Framework</a>.
	 * <br>
	 * <br>
	 *
	 * As a prerequisite to using this feature, the developer must register a <i>Client</i> with an <i>Authorization Server</i>.
	 * The output of the <i>Client Registration</i> process results in a number of properties that are then used for configuring
	 * an instance of a {@link org.springframework.security.oauth2.client.registration.ClientRegistration}.
	 * Properties specific to a <i>Client</i> include: <i>client_id</i>, <i>client_secret</i>, <i>scope</i>, <i>redirect_uri</i>, etc.
	 * There are also properties specific to the <i>Provider</i>, for example,
	 * <i>Authorization Endpoint URI</i>, <i>Token Endpoint URI</i>, <i>UserInfo Endpoint URI</i>, etc.
	 * <br>
	 * <br>
	 *
	 * Multiple client support is provided for use cases where the application provides the user the option
	 * for <i>&quot;Logging in&quot;</i> against one or more Providers, for example, <i>Google</i>, <i>GitHub</i>, <i>Facebook</i>, etc.
	 * <br>
	 * <br>
	 *
	 * {@link org.springframework.security.oauth2.client.registration.ClientRegistration}(s) are composed within a
	 * {@link org.springframework.security.oauth2.client.registration.ClientRegistrationRepository}.
	 * An instance of {@link org.springframework.security.oauth2.client.registration.ClientRegistrationRepository} is <b>required</b>
	 * and may be supplied via the {@link ApplicationContext} or configured using
	 * {@link OAuth2LoginConfigurer#clients(org.springframework.security.oauth2.client.registration.ClientRegistrationRepository)}.
	 * <br>
	 * <br>
	 *
	 * The default configuration provides an auto-generated login page at <code>&quot;/login&quot;</code> and
	 * redirects to <code>&quot;/login?error&quot;</code> when an authentication error occurs.
	 * The login page will display each of the clients (composed within the
	 * {@link org.springframework.security.oauth2.client.registration.ClientRegistrationRepository})
	 * with an anchor link to <code>&quot;/oauth2/authorization/code/{clientAlias}&quot;</code>.
	 * Clicking through the link will initiate the <i>&quot;Authorization Request&quot;</i> flow
	 * redirecting the end-user's user-agent to the <i>Authorization Endpoint</i> of the <i>Provider</i>.
	 * Assuming the <i>Resource Owner</i> (end-user) grants the <i>Client</i> access, the <i>Authorization Server</i>
	 * will redirect the end-user's user-agent to the <i>Redirection Endpoint</i> containing the <i>Authorization Code</i>
	 * - the <i>Redirection Endpoint</i> is automatically configured for the application and
	 * defaults to <code>&quot;/oauth2/authorize/code/{clientAlias}&quot;</code>.
	 *
	 * <p>
	 * At this point in the <i>&quot;authentication flow&quot;</i>, the configured
	 * {@link AuthorizationGrantTokenExchanger}
	 * will exchange the <i>Authorization Code</i> for an <i>Access Token</i> and then use it to access the protected resource
	 * at the <i>UserInfo Endpoint</i> (via {@link org.springframework.security.oauth2.client.user.OAuth2UserService})
	 * in order to retrieve the details of the <i>Resource Owner</i> (end-user) and establish the <i>&quot;authenticated&quot;</i> session.
	 *
	 * <h2>Example Configurations</h2>
	 *
	 * The minimal configuration defaults to automatically generating a login page at <code>&quot;/login&quot;</code>
	 * and redirecting to <code>&quot;/login?error&quot;</code> when an authentication error occurs or redirecting to
	 * <code>&quot;/&quot;</code> when an authenticated session is established.
	 *
	 * <pre>
	 * &#064;EnableWebSecurity
	 * public class OAuth2LoginSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 *		http
	 * 			.authorizeRequests()
	 * 				.anyRequest().authenticated()
	 * 				.and()
	 * 			.oauth2Login();
	 * 	}
	 *
	 *	&#064;Bean
	 *	public ClientRegistrationRepository clientRegistrationRepository() {
	 *		// ClientRegistrationRepositoryImpl must be composed of at least one ClientRegistration instance
	 *		return new ClientRegistrationRepositoryImpl();
	 *	}
	 * }
	 * </pre>
	 *
	 * The following shows the configuration options available for customizing the defaults.
	 *
	 * <pre>
	 * &#064;EnableWebSecurity
	 * public class OAuth2LoginSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 *		http
	 * 			.authorizeRequests()
	 * 				.anyRequest().authenticated()
	 * 				.and()
	 * 			.oauth2Login()
	 * 				.clients(this.clientRegistrationRepository())
	 * 				.authorizationRequestBuilder(this.authorizationRequestBuilder())
	 * 				.authorizationCodeTokenExchanger(this.authorizationCodeTokenExchanger())
	 * 				.userInfoEndpoint()
	 * 					.userInfoService(this.userInfoService())
	 * 				.userInfoEndpoint()
	 * 					// Provide a mapping between a Converter implementation and a UserInfo Endpoint URI
	 * 					.userInfoTypeConverter(this.userInfoConverter(),
	 * 									new URI("https://www.googleapis.com/oauth2/v3/userinfo"));
	 * 	}
	 *
	 *	&#064;Bean
	 *	public ClientRegistrationRepository clientRegistrationRepository() {
	 *		// ClientRegistrationRepositoryImpl must be composed of at least one ClientRegistration instance
	 *		return new ClientRegistrationRepositoryImpl();
	 *	}
	 *
	 * 	&#064;Bean
	 * 	public AuthorizationRequestUriBuilder authorizationRequestBuilder() {
	 * 		// Custom URI builder for the &quot;Authorization Request&quot;
	 * 		return new AuthorizationRequestUriBuilderImpl();
	 * 	}
	 *
	 * 	&#064;Bean
	 * 	public AuthorizationGrantTokenExchanger&lt;AuthorizationCodeAuthenticationToken&gt; authorizationCodeTokenExchanger() {
	 * 		// Custom implementation that exchanges an &quot;Authorization Code Grant&quot; for an &quot;Access Token&quot;
	 * 		return new AuthorizationCodeTokenExchangerImpl();
	 * 	}
	 *
	 * 	&#064;Bean
	 * 	public OAuth2UserService userInfoService() {
	 * 		// Custom implementation that retrieves the details of the authenticated user at the &quot;UserInfo Endpoint&quot;
	 * 		return new OAuth2UserServiceImpl();
	 * 	}
	 *
	 * 	&#064;Bean
	 * 	public Converter&lt;ClientHttpResponse, UserInfo&gt; userInfoConverter() {
	 * 		// Default converter implementation for UserInfo
	 * 		return new org.springframework.security.oauth2.client.user.converter.UserInfoConverter();
	 * 	}
	 * }
	 * </pre>
	 *
	 * @author Joe Grandja
	 * @since 5.0
	 * @see <a target="_blank" href="https://tools.ietf.org/html/rfc6749#section-4.1">Section 4.1 Authorization Code Grant Flow</a>
	 * @see <a target="_blank" href="https://tools.ietf.org/html/rfc6749#section-4.1.1">Section 4.1.1 Authorization Request</a>
	 * @see <a target="_blank" href="https://tools.ietf.org/html/rfc6749#section-4.1.2">Section 4.1.2 Authorization Response</a>
	 * @see org.springframework.security.oauth2.client.registration.ClientRegistration
	 * @see org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
	 * @see AuthorizationRequestUriBuilder
	 * @see AuthorizationGrantTokenExchanger
	 * @see org.springframework.security.oauth2.client.user.OAuth2UserService
	 *
	 * @return the {@link OAuth2LoginConfigurer} for further customizations
	 * @throws Exception
	 */
	public OAuth2LoginConfigurer<HttpSecurity> oauth2Login() throws Exception {
		return getOrApply(new OAuth2LoginConfigurer<HttpSecurity>());
	}

	/**
	 * Configures channel security. In order for this configuration to be useful at least
	 * one mapping to a required channel must be provided.
	 *
	 * <h2>Example Configuration</h2>
	 *
	 * The example below demonstrates how to require HTTPs for every request. Only
	 * requiring HTTPS for some requests is supported, but not recommended since an
	 * application that allows for HTTP introduces many security vulnerabilities. For one
	 * such example, read about <a
	 * href="http://en.wikipedia.org/wiki/Firesheep">Firesheep</a>.
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class ChannelSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http.authorizeRequests().antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;).and().formLogin()
	 * 				.and().requiresChannel().anyRequest().requiresSecure();
	 * 	}
	 *
	 * 	&#064;Override
	 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
	 * 		auth.inMemoryAuthentication().withUser(&quot;user&quot;).password(&quot;password&quot;).roles(&quot;USER&quot;);
	 * 	}
	 * }
	 * </pre>
	 *
	 *
	 * @return the {@link ChannelSecurityConfigurer} for further customizations
	 * @throws Exception
	 */
	public ChannelSecurityConfigurer<HttpSecurity>.ChannelRequestMatcherRegistry requiresChannel()
			throws Exception {
		ApplicationContext context = getContext();
		return getOrApply(new ChannelSecurityConfigurer<HttpSecurity>(context))
				.getRegistry();
	}

	/**
	 * Configures HTTP Basic authentication.
	 *
	 * <h2>Example Configuration</h2>
	 *
	 * The example below demonstrates how to configure HTTP Basic authentication for an
	 * application. The default realm is "Spring Security Application", but can be
	 * customized using {@link HttpBasicConfigurer#realmName(String)}.
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class HttpBasicSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http.authorizeRequests().antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;).and().httpBasic();
	 * 	}
	 *
	 * 	&#064;Override
	 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
	 * 		auth.inMemoryAuthentication().withUser(&quot;user&quot;).password(&quot;password&quot;).roles(&quot;USER&quot;);
	 * 	}
	 * }
	 * </pre>
	 *
	 * @return the {@link HttpBasicConfigurer} for further customizations
	 * @throws Exception
	 */
	public HttpBasicConfigurer<HttpSecurity> httpBasic() throws Exception {
		return getOrApply(new HttpBasicConfigurer<HttpSecurity>());
	}

	public <C> void setSharedObject(Class<C> sharedType, C object) {
		super.setSharedObject(sharedType, object);
	}

	@Override
	protected void beforeConfigure() throws Exception {
		setSharedObject(AuthenticationManager.class, getAuthenticationRegistry().build());
	}

	@Override
	protected DefaultSecurityFilterChain performBuild() throws Exception {
		Collections.sort(filters, comparator);
		return new DefaultSecurityFilterChain(requestMatcher, filters);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.springframework.security.config.annotation.web.HttpBuilder#authenticationProvider
	 * (org.springframework.security.authentication.AuthenticationProvider)
	 */
	public HttpSecurity authenticationProvider(
			AuthenticationProvider authenticationProvider) {
		getAuthenticationRegistry().authenticationProvider(authenticationProvider);
		return this;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.springframework.security.config.annotation.web.HttpBuilder#userDetailsService
	 * (org.springframework.security.core.userdetails.UserDetailsService)
	 */
	public HttpSecurity userDetailsService(UserDetailsService userDetailsService)
			throws Exception {
		getAuthenticationRegistry().userDetailsService(userDetailsService);
		return this;
	}

	private AuthenticationManagerBuilder getAuthenticationRegistry() {
		return getSharedObject(AuthenticationManagerBuilder.class);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.springframework.security.config.annotation.web.HttpBuilder#addFilterAfter(javax
	 * .servlet.Filter, java.lang.Class)
	 */
	public HttpSecurity addFilterAfter(Filter filter, Class<? extends Filter> afterFilter) {
		comparator.registerAfter(filter.getClass(), afterFilter);
		return addFilter(filter);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.springframework.security.config.annotation.web.HttpBuilder#addFilterBefore(
	 * javax.servlet.Filter, java.lang.Class)
	 */
	public HttpSecurity addFilterBefore(Filter filter,
			Class<? extends Filter> beforeFilter) {
		comparator.registerBefore(filter.getClass(), beforeFilter);
		return addFilter(filter);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.springframework.security.config.annotation.web.HttpBuilder#addFilter(javax.
	 * servlet.Filter)
	 */
	public HttpSecurity addFilter(Filter filter) {
		Class<? extends Filter> filterClass = filter.getClass();
		if (!comparator.isRegistered(filterClass)) {
			throw new IllegalArgumentException(
					"The Filter class "
							+ filterClass.getName()
							+ " does not have a registered order and cannot be added without a specified order. Consider using addFilterBefore or addFilterAfter instead.");
		}
		this.filters.add(filter);
		return this;
	}

	/**
	 * Adds the Filter at the location of the specified Filter class. For example, if you
	 * want the filter CustomFilter to be registered in the same position as
	 * {@link UsernamePasswordAuthenticationFilter}, you can invoke:
	 *
	 * <pre>
	 * addFilterAt(new CustomFilter(), UsernamePasswordAuthenticationFilter.class)
	 * </pre>
	 *
	 * @param filter the Filter to register
	 * @param atFilter the location of another {@link Filter} that is already registered
	 * (i.e. known) with Spring Security.
	 * @return the {@link HttpSecurity} for further customizations
	 */
	public HttpSecurity addFilterAt(Filter filter, Class<? extends Filter> atFilter) {
		this.comparator.registerAt(filter.getClass(), atFilter);
		return addFilter(filter);
	}

	/**
	 * Allows specifying which {@link HttpServletRequest} instances this
	 * {@link HttpSecurity} will be invoked on. This method allows for easily invoking the
	 * {@link HttpSecurity} for multiple different {@link RequestMatcher} instances. If
	 * only a single {@link RequestMatcher} is necessary consider using {@link #mvcMatcher(String)},
	 * {@link #antMatcher(String)}, {@link #regexMatcher(String)}, or
	 * {@link #requestMatcher(RequestMatcher)}.
	 *
	 * <p>
	 * Invoking {@link #requestMatchers()} will not override previous invocations of {@link #mvcMatcher(String)}},
	 * {@link #requestMatchers()}, {@link #antMatcher(String)},
	 * {@link #regexMatcher(String)}, and {@link #requestMatcher(RequestMatcher)}.
	 * </p>
	 *
	 * <h3>Example Configurations</h3>
	 *
	 * The following configuration enables the {@link HttpSecurity} for URLs that begin
	 * with "/api/" or "/oauth/".
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class RequestMatchersSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http
	 * 			.requestMatchers()
	 * 				.antMatchers(&quot;/api/**&quot;, &quot;/oauth/**&quot;)
	 * 				.and()
	 * 			.authorizeRequests()
	 * 				.antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;)
	 * 				.and()
	 * 			.httpBasic();
	 * 	}
	 *
	 * 	&#064;Override
	 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
	 * 		auth
	 * 			.inMemoryAuthentication()
	 * 				.withUser(&quot;user&quot;).password(&quot;password&quot;).roles(&quot;USER&quot;);
	 * 	}
	 * }
	 * </pre>
	 *
	 * The configuration below is the same as the previous configuration.
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class RequestMatchersSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http
	 * 			.requestMatchers()
	 * 				.antMatchers(&quot;/api/**&quot;)
	 * 				.antMatchers(&quot;/oauth/**&quot;)
	 * 				.and()
	 * 			.authorizeRequests()
	 * 				.antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;)
	 * 				.and()
	 * 			.httpBasic();
	 * 	}
	 *
	 * 	&#064;Override
	 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
	 * 		auth
	 * 			.inMemoryAuthentication()
	 * 				.withUser(&quot;user&quot;).password(&quot;password&quot;).roles(&quot;USER&quot;);
	 * 	}
	 * }
	 * </pre>
	 *
	 * The configuration below is also the same as the above configuration.
	 *
	 * <pre>
	 * &#064;Configuration
	 * &#064;EnableWebSecurity
	 * public class RequestMatchersSecurityConfig extends WebSecurityConfigurerAdapter {
	 *
	 * 	&#064;Override
	 * 	protected void configure(HttpSecurity http) throws Exception {
	 * 		http
	 * 			.requestMatchers()
	 * 				.antMatchers(&quot;/api/**&quot;)
	 * 				.and()
	 *			 .requestMatchers()
	 * 				.antMatchers(&quot;/oauth/**&quot;)
	 * 				.and()
	 * 			.authorizeRequests()
	 * 				.antMatchers(&quot;/**&quot;).hasRole(&quot;USER&quot;)
	 * 				.and()
	 * 			.httpBasic();
	 * 	}
	 *
	 * 	&#064;Override
	 * 	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
	 * 		auth
	 * 			.inMemoryAuthentication()
	 * 				.withUser(&quot;user&quot;).password(&quot;password&quot;).roles(&quot;USER&quot;);
	 * 	}
	 * }
	 * </pre>
	 *
	 * @return the {@link RequestMatcherConfigurer} for further customizations
	 */
	public RequestMatcherConfigurer requestMatchers() {
		return requestMatcherConfigurer;
	}

	/**
	 * Allows configuring the {@link HttpSecurity} to only be invoked when matching the
	 * provided {@link RequestMatcher}. If more advanced configuration is necessary,
	 * consider using {@link #requestMatchers()}.
	 *
	 * <p>
	 * Invoking {@link #requestMatcher(RequestMatcher)} will override previous invocations
	 * of {@link #requestMatchers()}, {@link #mvcMatcher(String)}, {@link #antMatcher(String)},
	 * {@link #regexMatcher(String)}, and {@link #requestMatcher(RequestMatcher)}.
	 * </p>
	 *
	 * @param requestMatcher the {@link RequestMatcher} to use (i.e. new
	 * AntPathRequestMatcher("/admin/**","GET") )
	 * @return the {@link HttpSecurity} for further customizations
	 * @see #requestMatchers()
	 * @see #antMatcher(String)
	 * @see #regexMatcher(String)
	 */
	public HttpSecurity requestMatcher(RequestMatcher requestMatcher) {
		this.requestMatcher = requestMatcher;
		return this;
	}

	/**
	 * Allows configuring the {@link HttpSecurity} to only be invoked when matching the
	 * provided ant pattern. If more advanced configuration is necessary, consider using
	 * {@link #requestMatchers()} or {@link #requestMatcher(RequestMatcher)}.
	 *
	 * <p>
	 * Invoking {@link #antMatcher(String)} will override previous invocations of {@link #mvcMatcher(String)}},
	 * {@link #requestMatchers()}, {@link #antMatcher(String)},
	 * {@link #regexMatcher(String)}, and {@link #requestMatcher(RequestMatcher)}.
	 * </p>
	 *
	 * @param antPattern the Ant Pattern to match on (i.e. "/admin/**")
	 * @return the {@link HttpSecurity} for further customizations
	 * @see AntPathRequestMatcher
	 */
	public HttpSecurity antMatcher(String antPattern) {
		return requestMatcher(new AntPathRequestMatcher(antPattern));
	}

	/**
	 * Allows configuring the {@link HttpSecurity} to only be invoked when matching the
	 * provided Spring MVC pattern. If more advanced configuration is necessary, consider using
	 * {@link #requestMatchers()} or {@link #requestMatcher(RequestMatcher)}.
	 *
	 * <p>
	 * Invoking {@link #mvcMatcher(String)} will override previous invocations of {@link #mvcMatcher(String)}},
	 * {@link #requestMatchers()}, {@link #antMatcher(String)},
	 * {@link #regexMatcher(String)}, and {@link #requestMatcher(RequestMatcher)}.
	 * </p>
	 *
	 * @param mvcPattern the Spring MVC Pattern to match on (i.e. "/admin/**")
	 * @return the {@link HttpSecurity} for further customizations
	 * @see MvcRequestMatcher
	 */
	public HttpSecurity mvcMatcher(String mvcPattern) {
		HandlerMappingIntrospector introspector = new HandlerMappingIntrospector(getContext());
		return requestMatcher(new MvcRequestMatcher(introspector, mvcPattern));
	}

	/**
	 * Allows configuring the {@link HttpSecurity} to only be invoked when matching the
	 * provided regex pattern. If more advanced configuration is necessary, consider using
	 * {@link #requestMatchers()} or {@link #requestMatcher(RequestMatcher)}.
	 *
	 * <p>
	 * Invoking {@link #regexMatcher(String)} will override previous invocations of {@link #mvcMatcher(String)}},
	 * {@link #requestMatchers()}, {@link #antMatcher(String)},
	 * {@link #regexMatcher(String)}, and {@link #requestMatcher(RequestMatcher)}.
	 * </p>
	 *
	 * @param pattern the Regular Expression to match on (i.e. "/admin/.+")
	 * @return the {@link HttpSecurity} for further customizations
	 * @see RegexRequestMatcher
	 */
	public HttpSecurity regexMatcher(String pattern) {
		return requestMatcher(new RegexRequestMatcher(pattern, null));
	}

	/**
	 * An extension to {@link RequestMatcherConfigurer} that allows optionally configuring
	 * the servlet path.
	 *
	 * @author Rob Winch
	 */
	public final class MvcMatchersRequestMatcherConfigurer extends RequestMatcherConfigurer {

		/**
		 * Creates a new instance
		 * @param context the {@link ApplicationContext} to use
		 * @param matchers the {@link MvcRequestMatcher} instances to set the servlet path
		 * on if {@link #servletPath(String)} is set.
		 */
		private MvcMatchersRequestMatcherConfigurer(ApplicationContext context,
				List<MvcRequestMatcher> matchers) {
			super(context);
			this.matchers = new ArrayList<RequestMatcher>(matchers);
		}

		public RequestMatcherConfigurer servletPath(String servletPath) {
			for (RequestMatcher matcher : this.matchers) {
				((MvcRequestMatcher) matcher).setServletPath(servletPath);
			}
			return this;
		}

	}

	/**
	 * Allows mapping HTTP requests that this {@link HttpSecurity} will be used for
	 *
	 * @author Rob Winch
	 * @since 3.2
	 */
	public class RequestMatcherConfigurer
			extends AbstractRequestMatcherRegistry<RequestMatcherConfigurer> {

		protected List<RequestMatcher> matchers = new ArrayList<RequestMatcher>();

		/**
		 * @param context
		 */
		private RequestMatcherConfigurer(ApplicationContext context) {
			setApplicationContext(context);
		}

		@Override
		public MvcMatchersRequestMatcherConfigurer mvcMatchers(HttpMethod method,
				String... mvcPatterns) {
			List<MvcRequestMatcher> mvcMatchers = createMvcMatchers(method, mvcPatterns);
			setMatchers(mvcMatchers);
			return new MvcMatchersRequestMatcherConfigurer(getContext(), mvcMatchers);
		}

		@Override
		public MvcMatchersRequestMatcherConfigurer mvcMatchers(String... patterns) {
			return mvcMatchers(null, patterns);
		}

		@Override
		protected RequestMatcherConfigurer chainRequestMatchers(
				List<RequestMatcher> requestMatchers) {
			setMatchers(requestMatchers);
			return this;
		}

		private void setMatchers(List<? extends RequestMatcher> requestMatchers) {
			this.matchers.addAll(requestMatchers);
			requestMatcher(new OrRequestMatcher(this.matchers));
		}

		/**
		 * Return the {@link HttpSecurity} for further customizations
		 *
		 * @return the {@link HttpSecurity} for further customizations
		 */
		public HttpSecurity and() {
			return HttpSecurity.this;
		}

	}

	/**
	 * If the {@link SecurityConfigurer} has already been specified get the original,
	 * otherwise apply the new {@link SecurityConfigurerAdapter}.
	 *
	 * @param configurer the {@link SecurityConfigurer} to apply if one is not found for
	 * this {@link SecurityConfigurer} class.
	 * @return the current {@link SecurityConfigurer} for the configurer passed in
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private <C extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity>> C getOrApply(
			C configurer) throws Exception {
		C existingConfig = (C) getConfigurer(configurer.getClass());
		if (existingConfig != null) {
			return existingConfig;
		}
		return apply(configurer);
	}
}
