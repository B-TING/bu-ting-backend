INSERT INTO local_chatroom (room_id, title, description, chat_zone, max_members, current_members)
VALUES
    (
        gen_random_uuid(),
        '수영구 & 남구 오픈채팅방',
        '광안리 해수욕장, 민락수변공원, 오륙도 스카이워크 인근 주민과 방문객을 위한 소통 공간입니다.',
        'SUYEONG_NAMGU',
        100,
        0
    ),

    (
        gen_random_uuid(),
        '해운대구 & 기장군 오픈채팅방',
        '해운대 해수욕장, 해동용궁사, 센텀시티 방문객들을 위한 자유로운 오픈채팅방입니다.',
        'HAEUNDAE_GIJANG',
        100,
        0
    ),

    (
        gen_random_uuid(),
        '금정구 & 동래구 & 연제구 & 부산진구 오픈채팅방',
        '서면, 전포, 사직, 범어사 등 부산 도심권 정보를 함께 공유하는 채팅방입니다.',
        'CENTRAL_NORTH',
        100,
        0
    ),

    (
        gen_random_uuid(),
        '서구 & 중구 & 동구 오픈채팅방',
        '국제시장, 자갈치시장, 용두산공원 등 부산 원도심 이야기를 나누는 공간입니다.',
        'OLD_DOWNTOWN',
        100,
        0
    ),

    (
        gen_random_uuid(),
        '영도구 오픈채팅방',
        '흰여울문화마을과 태종대 등 영도 지역 주민과 여행객을 위한 채팅방입니다.',
        'YEONGDO',
        100,
        0
    ),

    (
        gen_random_uuid(),
        '강서구 & 사상구 & 사하구 & 북구 오픈채팅방',
        '감천문화마을, 다대포 해수욕장, 김해국제공항 인근 정보를 공유하는 공간입니다.',
        'WESTERN_BUSAN',
        100,
        0
    );