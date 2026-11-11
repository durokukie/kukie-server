package com.duro.kukie.cluster.application.port.out

import com.duro.kukie.cluster.domain.AccessLevel
import java.time.Duration
import java.time.Instant

interface EksAccessGateway {

    /**
     * 역할을 맡을 수 있는지만 본다. 못 맡으면 AwsRoleNotAssumableException. 스택 알림 때 부르므로(역할을 다시 만들었을 수 있다)
     * 캐시된 세션을 버리고 새로 맡는다.
     */
    fun verify(target: AwsTarget)

    /**
     * 역할이 아직 있는지 캐시를 거치지 않고 실제로 맡아 본다. 그 대상의 캐시된 세션도 버리고, 새로 얻은 세션은 담지 않는다.
     * 맡으면 true, 거부(403)되면 false — 우리 ExternalId 로 거부되면 역할이 지워진 것이다. 그 밖의 오류(스로틀링·네트워크)는 던진다.
     */
    fun roleExists(target: AwsTarget): Boolean

    fun listClusters(target: AwsTarget, regions: List<String>): List<EksClusterSummary>

    fun describeCluster(target: AwsTarget, region: String, clusterName: String): EksClusterEndpoint

    /**
     * 역할 자신을 클러스터의 access entry 로 넣고 레벨의 정책을 붙인다 — FULL 이면 클러스터 관리자(클러스터 전체),
     * LIMITED 면 그 namespace 에 Edit. 이미 있으면 그대로 둔다. 정책이 거부되면 이번 호출이 만든 entry 는 지우고 던진다.
     */
    fun registerSelf(target: AwsTarget, region: String, clusterName: String, namespace: String, accessLevel: AccessLevel)

    /**
     * 역할 자신의 access entry 가 만든 지 권한이 퍼지기를 기다릴 만큼 지났는지 본다. 지났는데도 인증이 안 되면 스택을 다시 만들기 전의 옛 entry 다.
     * 템플릿 조건이 entry 에서 읽는 값이라, entry 가 없으면 고객 역할에는 EksAccessDeniedException 이 난다.
     */
    fun selfEntrySettled(target: AwsTarget, region: String, clusterName: String): Boolean

    /**
     * 역할 자신의 access entry 를 정리한다. keepNamespaces 가 비면 entry 를 지우고(없으면 그대로이나, 고객 역할은 템플릿 조건 때문에
     * EksAccessDeniedException 을 받는다), 남은 namespace 가 있으면 LIMITED 는 Edit 정책 범위를 그 namespace 들로 좁히고 남아 있을 수 있는 관리자 정책을 뗀다 — 같은 EKS 를 다른
     * namespace 로 연결한 등록이 끊기지 않게. FULL 은 클러스터 전체 범위라 그대로 둔다.
     */
    fun unregisterSelf(
        target: AwsTarget,
        region: String,
        clusterName: String,
        keepNamespaces: Set<String>,
        accessLevel: AccessLevel,
    )

    /**
     * 스택의 레벨이 바뀌었을 때 이미 등록한 EKS 의 정책을 바꾼다. 새 레벨의 정책을 먼저 붙이고(LIMITED 면 namespaces 범위)
     * 다른 레벨의 정책을 뗀다. entry 가 없으면 만든다. LIMITED 로 내리다 끝내 거부되면 정책 없는 entry 가 되더라도 관리자 정책을 떼고 던진다.
     */
    fun changeAccessLevel(
        target: AwsTarget,
        region: String,
        clusterName: String,
        namespaces: Set<String>,
        accessLevel: AccessLevel,
    )

    /**
     * namespace 의 default ServiceAccount 를 쿠버네티스 API 로 읽어 namespace 가 있는지 본다.
     * waitForGrant 면 막 붙인 권한이 퍼질 때까지 401·403 을 몇 번 다시 묻는다.
     */
    fun namespaceStatus(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        waitForGrant: Boolean,
    ): NamespaceStatus

    /** 버전·엔드포인트와 애드온·노드 그룹·서브넷을 읽는다. v3 권한이 없으면 inventory 를 비워 돌려준다. */
    fun describeSetup(target: AwsTarget, region: String, clusterName: String): EksClusterSetup

    fun issueToken(target: AwsTarget, region: String, clusterName: String): String

    /** 관리형 노드 그룹 전부. 읽기 권한(템플릿 v3)이 없으면 EksAccessDeniedException. */
    fun listNodegroupStates(target: AwsTarget, region: String, clusterName: String): List<EksNodegroupState>

    /** 노드 그룹 하나. 없으면 null. */
    fun describeNodegroupState(target: AwsTarget, region: String, clusterName: String, nodegroupName: String): EksNodegroupState?

    /**
     * desired 를 바꾼다. min·max 는 current 의 값을 그대로 함께 보내 바꾸지 않는다. 거부되면 GpuNodeControlDeniedException, 다른 변경이 진행 중이면 NodeGroupBusyException,
     * 범위를 벗어나면 InvalidNodeCountException, 없으면 GpuNodeGroupNotFoundException.
     */
    fun updateNodegroupDesired(target: AwsTarget, region: String, clusterName: String, current: EksNodegroupState, desired: Int)

    /** 그 namespace 에 ServedModel 이 하나라도 있는지 쿠버네티스 API 로 본다. */
    fun hasServedModel(target: AwsTarget, region: String, clusterName: String, endpoint: EksClusterEndpoint, namespace: String): Boolean

    /**
     * 쿠버네티스 API 로 ServedModel 을 만든다. 404 는 GpuAddonNotInstalledException, 401·403 은 GpuServingDeniedException,
     * 409 는 ServedModelExistsException, 그 밖의 실패는 GpuClusterApiFailedException.
     */
    fun createServedModel(target: AwsTarget, region: String, clusterName: String, endpoint: EksClusterEndpoint, manifest: ServedModelManifest)

    /** 사전 검사 Job 을 만든다. 같은 이름이 이미 있으면(같은 초에 두 번 눌렀다) 그대로 둔다. 401·403 은 GpuServingDeniedException, 그 밖의 실패는 GpuClusterApiFailedException. */
    fun createPrecheckJob(target: AwsTarget, region: String, clusterName: String, endpoint: EksClusterEndpoint, manifest: PrecheckJobManifest)

    /** 사전 검사 Job 의 Pod 상태를 읽는다. Pod 가 없으면(시간이 지나 지워졌다) null. 실패는 createPrecheckJob 과 같다. */
    fun getPrecheckPod(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        jobName: String,
    ): PrecheckPodState?

    /** 쿠버네티스 API 로 ServedModel 을 지운다. 404 는 ServedModelNotFoundException, 나머지는 createServedModel 과 같다. */
    fun deleteServedModel(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        name: String,
    )

    /** 쿠버네티스 API 로 ServedModel 을 읽는다. 404 는 ServedModelNotFoundException, 401·403 은 GpuServingDeniedException, 그 밖의 실패는 GpuClusterApiFailedException. */
    fun getServedModel(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        name: String,
    ): ServedModelState

    /**
     * annotation serving.kukie.io/keep-until 을 쓴다. resourceVersion 이 그사이 바뀌었으면 쓰지 않고 false — 다른 사람의 더 긴 연장을 덮어
     * 줄이지 않게 하려는 것이다. 다른 실패는 getServedModel 과 같다.
     */
    fun keepServedModelUntil(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        name: String,
        keepUntil: Instant,
        resourceVersion: String,
    ): Boolean

    /**
     * annotation serving.kukie.io/measure 에 측정 요청 값을 쓴다. 값이 바뀌면 오븐이 새 측정으로 본다. resourceVersion 이 그사이 바뀌었으면
     * 쓰지 않고 false — 읽은 뒤 측정이 시작됐을 수 있어서 다시 읽고 판단해야 한다. 다른 실패는 getServedModel 과 같다.
     */
    fun requestServedModelMeasure(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        name: String,
        request: String,
        resourceVersion: String,
    ): Boolean

    /**
     * API 서버의 Service 프록시로 그 모델에 /v1/chat/completions 를 보낸다. timeout 은 응답 헤더까지의 시간이다. 호출한 스레드를 인터럽트하면 멈춘다.
     * 401·403 은 GpuServingDeniedException, 404·503 은 ModelNotReadyException, 400·413·422 는 ModelChatRejectedException,
     * 429 는 ModelChatBusyException, 그 밖의 실패는 GpuClusterApiFailedException. 응답 본문은 읽지 않는다.
     */
    fun openModelChat(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        name: String,
        body: ByteArray,
        timeout: Duration,
    ): ModelChatStream
}

/** 고객 클러스터에 잠깐 띄우는 네트워크 검사 Job. script 는 서버가 정한 값만 담는다. */
data class PrecheckJobManifest(
    val namespace: String,
    val name: String,
    val image: String,
    val script: String,
)

data class PrecheckPodState(
    val phase: String?,
    /** 컨테이너가 시작하지 못한 이유. 이미지를 못 받으면 ErrImagePull·ImagePullBackOff. */
    val waitingReason: String?,
    /** 스케줄러가 Pod 를 놓지 못했다. */
    val unschedulable: Boolean,
    /** 컨테이너가 끝나며 남긴 검사 결과. 고객 클러스터에서 온 값이라 그대로 믿지 않는다. */
    val terminationMessage: String?,
)
