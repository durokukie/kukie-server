package com.duro.kukie.cluster.presentation.dto.response

/** 등록 화면에서 고를 namespace. 이름순이고 kube- 로 시작하는 시스템 namespace 와 지우는 중인 것은 뺐다. */
data class EksNamespacesResponse(
    val namespaces: List<String>,
)
