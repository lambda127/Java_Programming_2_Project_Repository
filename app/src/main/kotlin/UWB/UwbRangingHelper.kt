package UWB

import android.content.Context
import android.util.Log
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
    private val TAG = "UwbRangingHelper"

    init {
        scope.launch {
            Log.d(TAG, "UWB Manager 초기화 시작")
            uwbManager = UwbManager.createInstance(context)
            Log.d(TAG, "UWB Manager 초기화 완료")
        }
    }

    // 1단계: 내 기기의 UWB 주소 가져오기
    fun prepareLocalAddress(isController: Boolean) {
        Log.d(TAG, "prepareLocalAddress 호출됨 - isController: $isController")
        scope.launch {
            try {
                val manager = uwbManager ?: run {
                    Log.e(TAG, "UwbManager가 null입니다.")
                    throw IllegalStateException("UwbManager 초기화 실패")
                }
                val sessionScope = if (isController) {
                    Log.d(TAG, "Controller session scope 생성 중")
                    manager.controllerSessionScope()
                } else {
                    Log.d(TAG, "Controlee session scope 생성 중")
                    manager.controleeSessionScope()
                }
                val addressBytes = sessionScope.localAddress.address
                Log.d(TAG, "로컬 주소 수신: ${addressBytes.joinToString { "%02X".format(it) }}")
                callback.onLocalAddressReceived(addressBytes)
            } catch (e: Exception) {
                Log.e(TAG, "주소 생성 실패", e)
                callback.onRangingError("주소 생성 실패: ${e.message}")
            }
        }
    }

    // 2단계: Ranging 시작
    fun startRanging(remoteAddress: ByteArray, sessionId: Int, isController: Boolean) {
        Log.d(TAG, "startRanging 호출됨 - remoteAddress: ${remoteAddress.joinToString { "%02X".format(it) }}, sessionId: $sessionId, isController: $isController")
        stopRanging()

        rangingJob = scope.launch {
            try {
                val manager = uwbManager ?: run {
                    Log.e(TAG, "startRanging: UwbManager가 null입니다.")
                    return@launch
                }
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

                Log.d(TAG, "Ranging session 준비 중...")
                sessionScope.prepareSession(parameters)
                    .onEach { result ->
                        when(result) {
                            is RangingResult.RangingResultPosition -> {
                                val distance = result.position.distance
                                if (distance != null) {
                                    Log.d(TAG, "Ranging 성공: 거리 = ${distance.value}m")
                                    callback.onRangingResult(distance.value)
                                } else {
                                    Log.d(TAG, "Ranging 결과에 거리가 포함되지 않음")
                                }
                            }
                            is RangingResult.RangingResultPeerDisconnected -> {
                                Log.w(TAG, "상대방 연결 끊김")
                                callback.onRangingError("Peer disconnected")
                                stopRanging()
                            }
                            else -> {
                                Log.d(TAG, "처리되지 않은 Ranging 결과: $result")
                            }
                        }
                    }
                    .catch { e ->
                        Log.e(TAG, "Ranging flow에서 오류 발생", e)
                        callback.onRangingError("Ranging flow error: ${e.message}")
                    }
                    .launchIn(this)
                Log.d(TAG, "Ranging session 시작됨")

            } catch (e: Exception) {
                Log.e(TAG, "Ranging 시작 실패", e)
                callback.onRangingError("Ranging 시작 실패: ${e.message}")
            }
        }
    }

    fun stopRanging() {
        if (rangingJob?.isActive == true) {
            Log.d(TAG, "stopRanging 호출됨, Ranging 중단")
            rangingJob?.cancel()
        }
        rangingJob = null
        callback.onRangingComplete()
    }
}