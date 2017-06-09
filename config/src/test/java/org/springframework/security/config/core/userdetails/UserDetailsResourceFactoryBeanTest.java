/*
 *
 *  * Copyright 2002-2017 the original author or authors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.springframework.security.config.core.userdetails;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.util.InMemoryResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * @author Rob Winch
 * @since 5.0
 */
@RunWith(MockitoJUnitRunner.class)
public class UserDetailsResourceFactoryBeanTest {
	String location = "classpath:users.properties";

	@Mock
	ResourceLoader resourceLoader;

	UserDetailsResourceFactoryBean factory = new UserDetailsResourceFactoryBean();

	@Test
	public void getObjectWhenResourceLoaderNullThenThrowsIllegalStateException() throws Exception {
		factory.setPropertiesResourceLocation(location);

		assertThatThrownBy(() -> factory.getObject() )
			.isInstanceOf(IllegalArgumentException.class)
			.hasStackTraceContaining("resourceLoader cannot be null if propertiesResource is null");
	}

	@Test
	public void getObjectWhenPropertiesResourceLocationNullThenThrowsIllegalStateException() throws Exception {
		factory.setResourceLoader(resourceLoader);

		assertThatThrownBy(() -> factory.getObject() )
			.isInstanceOf(IllegalStateException.class)
			.hasStackTraceContaining("Either propertiesResource cannot be null or both resourceLoader and propertiesResourceLocation cannot be null");
	}

	@Test
	public void getObjectWhenPropertiesResourceLocationSingleUserThenThrowsGetsSingleUser() throws Exception {
		setResource("user=password,ROLE_USER");

		Collection<UserDetails> users = factory.getObject();

		UserDetails expectedUser = User.withUsername("user")
			.password("password")
			.authorities("ROLE_USER")
			.build();
		assertThat(users).containsExactly(expectedUser);
	}

	@Test
	public void getObjectWhenPropertiesResourceSingleUserThenThrowsGetsSingleUser() throws Exception {
		factory.setPropertiesResource(new InMemoryResource("user=password,ROLE_USER"));

		Collection<UserDetails> users = factory.getObject();

		UserDetails expectedUser = User.withUsername("user")
			.password("password")
			.authorities("ROLE_USER")
			.build();
		assertThat(users).containsExactly(expectedUser);
	}

	@Test
	public void getObjectWhenInvalidUserThenThrowsMeaningfulException() throws Exception {
		setResource("user=invalidFormatHere");


		assertThatThrownBy(() -> factory.getObject() )
			.isInstanceOf(IllegalStateException.class)
			.hasStackTraceContaining("user")
			.hasStackTraceContaining("invalidFormatHere");
	}

	private void setResource(String contents) throws IOException {
		Resource resource = new InMemoryResource(contents);
		when(resourceLoader.getResource(location)).thenReturn(resource);

		factory.setPropertiesResourceLocation(location);
		factory.setResourceLoader(resourceLoader);
	}
}
