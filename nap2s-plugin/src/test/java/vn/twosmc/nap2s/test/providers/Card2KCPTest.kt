package vn.twosmc.nap2s.test.providers

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.config.MainConfig
import vn.twosmc.nap2s.platform.db.NapPlugin
import vn.twosmc.nap2s.providers.types.Card2KCP
import vn.twosmc.nap2s.providers.types.ChargingWsV2CP
import vn.twosmc.nap2s.test.utils.setInternal
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

/**
 * Test Card2KCP — kiểm tra URL status và API URL.
 *
 * - Nap2S.instance = NapPlugin.Companion.instance
 * - Test chỉ verify URL format, không gọi API thật
 * - getRequestParameters() cần đầy đủ mock definition → integration test riêng
 */
class Card2KCPTest {
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    lateinit var mockCard2K: Card2KCP

    @Mock
    lateinit var mockedMain: Nap2S

    @Mock
    lateinit var mockedConfig: MainConfig

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        initMockedCard2K()

        // Set Nap2S.instance qua reflection (lateinit companion)
        NapPlugin::class.java.getDeclaredField("instance").apply {
            isAccessible = true
            set(null, mockedMain)
        }

        Mockito.doReturn(mockedConfig).`when`(mockedMain).config
        Mockito.doReturn("testserver").`when`(mockedConfig).server
    }

    /** Kiểm tra status URL không null. */
    @Test
    fun testStatus() {
        val statusUrl = mockCard2K.getStatusUrl()
        println("Status URL: $statusUrl")
        assertNotNull(statusUrl)
    }

    /** Kiểm tra API URL đúng endpoint Card2K. */
    @Test
    fun testApiUrl() {
        val method = ChargingWsV2CP::class.java.getDeclaredMethod("getApiUrl")
        method.isAccessible = true
        val apiUrl = method.invoke(mockCard2K) as String
        assertEquals("https://card2k.com/chargingws/v2", apiUrl)
    }

    private fun initMockedCard2K() {
        mockCard2K.setInternal("partnerId", System.getenv("CARD2K_PARTNER_ID") ?: "test_partner_id")
        mockCard2K.setInternal("partnerKey", System.getenv("CARD2K_PARTNER_KEY") ?: "test_partner_key")
        mockCard2K.setInternal("apiUrl", "https://card2k.com/chargingws/v2")
    }
}