/*
 *     Copyright 2026 Philterd, LLC @ https://www.philterd.ai
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.philterd.philter.data.providers;

import ai.philterd.philter.data.entities.ApiKeyEntity;
import ai.philterd.philter.data.services.ApiKeyDataService;
import com.vaadin.flow.data.provider.Query;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every dashboard grid pages its rows through one of these, and each is constructed with the id of the
 * user whose page it is. That id is the whole of the dashboard's tenant isolation: a provider that
 * dropped it, or reused one from another page, would list one account's policies, lists, contexts or
 * API keys to another. Nothing tested them.
 *
 * <p>Each test asks for a page and a count as a grid would, and checks the id that reached the service
 * — and that no other id did.
 */
class DataProviderScopingTest {

    private static final ObjectId OWNER = new ObjectId();
    private static final ObjectId SOMEONE_ELSE = new ObjectId();

    /** A grid's first request: 25 rows from the top, unsorted. */
    private static <T> Query<T, Void> firstPage() {
        return new Query<>(0, 25, List.of(), null, null);
    }

    @Test
    @DisplayName("API keys are paged and counted for their owner alone")
    void apiKeysAreScopedToTheirOwner() {

        final ApiKeyDataService service = mock(ApiKeyDataService.class);
        when(service.findAll(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(service.count(any())).thenReturn(0);

        final ApiKeyEntityDataProvider provider = new ApiKeyEntityDataProvider(OWNER, service);

        provider.fetch(firstPage()).count();
        provider.size(firstPage());

        verify(service).findAll(OWNER, 0, 25);
        verify(service).count(OWNER);
        verify(service, never()).findAll(eq(SOMEONE_ELSE), anyInt(), anyInt());
        verify(service, never()).count(SOMEONE_ELSE);

    }

    @Test
    @DisplayName("What the service returns is what the grid gets")
    void rowsArePassedThroughUntouched() {

        final ApiKeyDataService service = mock(ApiKeyDataService.class);
        final ApiKeyEntity first = new ApiKeyEntity();
        final ApiKeyEntity second = new ApiKeyEntity();
        when(service.findAll(any(), anyInt(), anyInt())).thenReturn(List.of(first, second));

        final List<ApiKeyEntity> fetched = new ApiKeyEntityDataProvider(OWNER, service)
                .fetch(firstPage()).toList();

        assertEquals(List.of(first, second), fetched);

    }

}
