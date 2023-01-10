package com.woocommerce.android.util

import com.woocommerce.android.BuildConfig
import com.woocommerce.android.ui.jitm.QueryParamsEncoder
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.URLEncoder

class QueryParamsEncoderTest {
    private val buildConfigWrapper: BuildConfigWrapper = mock()
    private val queryParramsEncoder = QueryParamsEncoder(buildConfigWrapper)

    @Test
    fun `given debug build, when getEncodedQueryParams called, then proper encoded query params returned`() {
        // GIVEN
        whenever(buildConfigWrapper.debug).thenReturn(BuildConfig.DEBUG)

        // WHEN
        val encoderQueryParams = queryParramsEncoder.getEncodedQueryParams()

        // THEN
        assertThat(encoderQueryParams).isEqualTo(
            URLEncoder.encode(
                "build_type=developer&platform=android&version=${BuildConfig.VERSION_NAME}",
                Charsets.UTF_8.name()
            )
        )
    }

    @Test
    fun `given release build, when getEncodedQueryParams called, then proper encoded query params returned`() {
        // GIVEN
        whenever(buildConfigWrapper.debug).thenReturn(false)

        // WHEN
        val encoderQueryParams = queryParramsEncoder.getEncodedQueryParams()

        // THEN
        assertThat(encoderQueryParams).isEqualTo(
            URLEncoder.encode(
                "platform=android&version=${BuildConfig.VERSION_NAME}",
                Charsets.UTF_8.name()
            )
        )
    }
}
