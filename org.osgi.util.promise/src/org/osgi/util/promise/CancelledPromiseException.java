/*
 * Copyright (c) OSGi Alliance (2014). All Rights Reserved.
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

package org.osgi.util.promise;

/**
 * This exception is not normally thrown. Typically it is used to resolve a
 * {@link Promise} in the event that it is cancelled by the client before it is
 * resolved normally.
 * 
 * @author $Id$
 */
public class CancelledPromiseException extends RuntimeException {

	private static final long	serialVersionUID	= 1L;

	CancelledPromiseException() {
		super();
	}
}