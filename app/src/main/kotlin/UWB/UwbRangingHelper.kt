package UWB

import android.content.Context
import androidx.core.uwb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

// Java로 결과를 전달하기 위한 인터페이스
interface UwbRangingCallback {
    fun onLocalAddressReceived(address: ByteArray)
    fun onRangingResult(distance: Float)
    fun onRangingError(error: String)
    fun onRangingComplete()
}

class UwbRangingHelper(private val context: Context, private val callback: UwbRangingCallback) {

    private var uwbManager: UwbManager? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var rangingJob: Job? = null

    init {
        scope.launch {
            uwbManager = UwbManager.createInstance(context)
        }
    }

    // 1단계: 내 기기의 UWB 주소 가져오기
    fun prepareLocalAddress(isController: Boolean) {
        scope.launch {
            try {
                val manager = uwbManager ?: throw IllegalStateException("UwbManager 초기화 실패")
                val sessionScope = if (isController) {
                    manager.controllerSessionScope()
                } else {
                    manager.controleeSessionScope()
                }
                val addressBytes = sessionScope.localAddress.address
                callback.onLocalAddressReceived(addressBytes)
            } catch (e: Exception) {
                callback.onRangingError("주소 생성 실패: ${e.message}")
            }
        }
    }

    // 2단계: Ranging 시작
    fun startRanging(remoteAddress: ByteArray, sessionId: Int, isController: Boolean) {
        stopRanging()

        rangingJob = scope.launch {
            try {
                val manager = uwbManager ?: return@launch
                val sessionScope = if (isController) {
                    manager.controllerSessionScope()
                } else {
                    manager.controleeSessionScope()
                }

                val partnerAddress = UwbAddress(remoteAddress)
                val partnerDevice = UwbDevice.createForAddress(partnerAddress.address)

                val parameters = RangingParameters(
                    uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
                    sessionId = sessionId,
                    subSessionId = 0,
                    sessionKeyInfo = null,
                    subSessionKeyInfo = null,
                    complexChannel = null,
                    peerDevices = listOf(partnerDevice),
                    updateRateType = RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC
                )

                sessionScope.prepareSession(parameters)
                    .onEach { result ->
                        when(result) {
                            is RangingResult.RangingResultPosition -> {
                                val distance = result.position.distance
                                if (distance != null) {
                                    callback.onRangingResult(distance.value)
                                }
                            }
                            is RangingResult.RangingResultPeerDisconnected -> {
                                callback.onRangingError("Peer disconnected")
                                stopRanging()
                            }
                            else -> {
                                // Other unhandled results
                            }
                        }
                    }
                    .catch { e ->
                        callback.onRangingError("Ranging flow error: ${e.message}")
                    }
                    .launchIn(this)

            } catch (e: Exception) {
                callback.onRangingError("Ranging 시작 실패: ${e.message}")
            }
        }
    }

    fun stopRanging() {
        rangingJob?.cancel()
        rangingJob = null
        callback.onRangingComplete()
    }
}