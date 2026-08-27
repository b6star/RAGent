package com.yourssu.ragent.mock

import com.yourssu.ragent.model.ChatMessage
import com.yourssu.ragent.model.Person

const val CurrentUserId = "uid-me"

fun mockPeople() = listOf(
    Person(id = CurrentUserId, name = "나"),
    Person(id = "uid-junseong", name = "준성"),
    Person(id = "uid-minji", name = "민지"),
    Person(id = "uid-doyun", name = "도윤"),
    Person(id = "uid-seoyeon", name = "서연")
)

fun mockMessages() = listOf(
    mockMessage("msg-1", "안녕하세요.", "uid-junseong", CurrentUserId, "project-ragent", 543, true),
    mockMessage("msg-2", "이번 주 변경사항은 Docs 탭부터 정리합시다.", "uid-junseong", CurrentUserId, "project-ragent", 570, true),
    mockMessage("msg-3", "네, Docs Mock 먼저 정리해두겠습니다.", CurrentUserId, "uid-junseong", "project-ragent", 585),
    mockMessage("msg-4", "Repository 화면은 README 우선 노출로 가죠.", "uid-junseong", CurrentUserId, "project-ragent", 602),
    mockMessage("msg-5", "성적 탭 Mock 데이터 확인 부탁드립니다.", "uid-minji", CurrentUserId, "project-focuswave", 604),
    mockMessage("msg-6", "확인했습니다. 리스트 스크롤 케이스도 추가할게요.", CurrentUserId, "uid-minji", "project-focuswave", 620),
    mockMessage("msg-7", "보낸 메시지 탭에서도 레이아웃 확인 부탁드립니다.", "uid-minji", CurrentUserId, "project-focuswave", 1044),
    mockMessage("msg-8", "채플 탭 담당 범위 공유했습니다.", "uid-doyun", CurrentUserId, "project-ragent", 678),
    mockMessage("msg-9", "멤버 탭은 관리자/팀원만 보이게 처리했습니다.", CurrentUserId, "uid-doyun", "project-ragent", 822),
    mockMessage("msg-10", "채팅 상세 화면 스크롤 테스트용 메시지입니다.", "uid-doyun", CurrentUserId, "project-ragent", 1051),
    mockMessage("msg-11", "문서 접근 권한 확인 부탁드립니다.", "uid-seoyeon", CurrentUserId, "project-soongsil-life", 722),
    mockMessage("msg-12", "열람자 권한은 문서 확인 중심으로 잡아둘게요.", CurrentUserId, "uid-seoyeon", "project-soongsil-life", 750),
    mockMessage("msg-13", "메모: Members 탭 긴 담당 문구 줄바꿈 확인", CurrentUserId, CurrentUserId, null, 1210),
    mockMessage("msg-14", "메모: 전체 메시지 받은/보낸 토글 스크롤 상태 확인", CurrentUserId, CurrentUserId, null, 1212),
    mockMessage("msg-15", "메모: Firebase 연결 전까지 전송 버튼은 입력값만 비우기", CurrentUserId, CurrentUserId, null, 1215),
    mockMessage("msg-16", "메모: 프로젝트 생성 바텀시트 디자인 점검", CurrentUserId, CurrentUserId, null, 1218)
)

private fun mockMessage(
    id: String,
    text: String,
    senderId: String,
    receiverId: String,
    projectId: String?,
    minute: Int,
    isNotice: Boolean = false
) = ChatMessage(
    id = id,
    text = text,
    createdAt = minute * 60_000L,
    senderId = senderId,
    receiverId = receiverId,
    projectId = projectId,
    isNotice = isNotice
)
