클라이언트 코드 구조 및 흐름에 대한 분석은 다음과 같습니다. 이 문서는 프로젝트를 처음 접하는 개발자도 쉽게 이해할 수 있도록 상세하게 작성되었습니다.

### 1. 전체 아키텍처 및 조직

이 클라이언트 애플리케이션은 클라이언트-서버 아키텍처를 기반으로 하며, Java Swing을 사용하여 GUI가 구현되었습니다. 전체적으로는 MVC(Model-View-Controller) 패턴과 유사한 형태로 구성되어 있습니다:

*   **View (UI Panels):** `LoginPanel`, `LobbyPanel`, `RoomWaitingPanel`, `GamePanel`, `ResultPanel` 등 사용자에게 보여지는 화면을 담당하는 `JPanel` 서브클래스들입니다. 이들은 사용자 입력을 받고 시각적인 요소를 렌더링합니다.
*   **Model (GameStateManager):** 애플리케이션의 모든 상태(사용자 인증 정보, 방 정보, 게임 진행 상태 등)를 중앙 집중적으로 관리하는 싱글톤 클래스입니다. 데이터와 비즈니스 로직을 포함합니다.
*   **Controller (BaseballClientGUI):** 메인 `JFrame`이자 모든 UI 패널의 이벤트를 수신하고, 서버로부터의 메시지를 처리하며, `GameStateManager`를 업데이트하고 화면 전환을 조정하는 중앙 컨트롤러 역할을 합니다.

각 UI 구성 요소, 네트워크 통신, 상태 관리 등 각기 다른 책임 영역을 명확하게 분리하여 모듈성을 높였습니다. 또한, UI 이벤트 처리를 위해 Java Swing의 리스너 패턴(Listener Pattern)을 적극적으로 활용합니다.

### 2. 주요 구성 요소 및 역할

*   **`BaseballClientGUI` (client/BaseballClientGUI.java)**:
    *   애플리케이션의 메인 창 (`JFrame`)이며, `main` 메서드를 통해 시작됩니다.
    *   `CardLayout`을 사용하여 `LoginPanel`, `LobbyPanel`, `RoomWaitingPanel`, `GamePanel`, `ResultPanel` 등 다양한 UI 패널(화면)을 전환합니다.
    *   `MessageHandler` 인터페이스를 구현하여 `NetworkManager`로부터 서버 메시지를 비동기적으로 수신하고, 메시지 타입(`MessageType`)에 따라 적절한 `handleXxxResponse` 또는 `handleXxxUpdate` 메서드로 디스패치합니다.
    *   각 UI 패널의 리스너 인터페이스(`LoginListener`, `LobbyListener`, `RoomWaitingListener`, `GamePanelListener`, `ResultPanelListener`)를 구현하여 사용자 이벤트를 처리합니다.
    *   `NetworkManager`와 `GameStateManager` 인스턴스를 생성하고 관리하며, 서버 응답이나 사용자 동작에 따라 UI 화면을 전환하고 상태를 업데이트하는 중앙 조정자 역할을 합니다.
*   **`NetworkManager` (client/network/NetworkManager.java)**:
    *   서버와의 모든 네트워크 통신을 관리하는 핵심 클래스입니다.
    *   `server.txt` 파일에서 서버 주소(`serverAddress`)와 포트(`serverPort`) 정보를 로드하며, 파일이 없거나 잘못된 경우 기본값(`localhost:54321`)을 사용합니다.
    *   `java.net.Socket`을 사용하여 TCP 연결을 설정하고, `ObjectOutputStream`과 `ObjectInputStream`을 통해 클라이언트와 서버 간에 `common.Message` 객체를 직렬화/역직렬화하여 송수신합니다.
    *   서버로부터 메시지를 비동기적으로 수신하기 위해 별도의 스레드(`receiveThread`)를 사용합니다. 이는 GUI 스레드의 블로킹을 방지합니다.
    *   수신된 메시지는 `SwingUtilities.invokeLater()`를 통해 UI 스레드(Event Dispatch Thread, EDT)에서 `MessageHandler` 인터페이스(구현체는 `BaseballClientGUI`)의 `handleMessage()` 메서드로 전달됩니다.
*   **`GameStateManager` (client/state/GameStateManager.java)**:
    *   애플리케이션의 모든 중요한 상태를 저장하고 관리하는 싱글톤 클래스입니다.
    *   현재 로그인된 사용자 정보(`currentUserId`, `isAuthenticated`), 사용자 상태(`currentUserStatus`), 현재 참여 중인 방의 정보(`currentRoomId`, `currentRoomName`, `roomMasterUserId`, `currentGameMode`, `currentDifficulty`, `currentTurnTimeLimit`), 방에 있는 플레이어 목록 및 준비 상태(`roomPlayersList`, `playerReadyStatus`), 현재 게임의 진행 상태(`currentGameId`, `currentRound`, `currentTurnPlayerId`, `myAnswerKey`, `turnTimer` 등)를 포함합니다.
    *   `BaseballClientGUI` 및 다른 UI 패널들이 `GameStateManager.getInstance()`를 통해 이 인스턴스에 접근하여 상태를 읽거나 업데이트합니다.
    *   `resetAllState()`, `resetRoomState()`, `resetGameState()`와 같은 상태 초기화 메서드를 제공합니다.
*   **UI 패널 (`LoginPanel`, `LobbyPanel`, `RoomWaitingPanel`, `GamePanel`, `ResultPanel`) (client/ui/*.java)**:
    *   각각 특정 화면의 사용자 인터페이스를 렌더링하고 사용자 입력을 받습니다.
    *   자신에게 발생한 사용자 이벤트를 해당 리스너 인터페이스를 통해 `BaseballClientGUI`에 알립니다.
    *   `GameStateManager`에서 직접 상태를 읽어와 UI를 업데이트하기도 합니다(예: `RoomWaitingPanel`이 `stateManager`에서 플레이어 목록과 방 정보를 가져옴).
    *   `UIHelper` 유틸리티 클래스를 사용하여 공통적인 UI 작업을 처리합니다.
*   **리스너 인터페이스 (`LoginListener`, `LobbyListener`, `RoomWaitingListener`, `GamePanelListener`, `ResultPanelListener`) (client/ui/*.java)**:
    *   각 UI 패널에서 발생하는 사용자 이벤트에 대한 콜백 메서드를 정의합니다.
    *   `BaseballClientGUI`가 이 모든 인터페이스를 구현하여 각 패널의 이벤트를 수신하고 처리합니다. 이는 UI와 컨트롤러 간의 느슨한 결합을 유지하는 데 도움을 줍니다.
*   **`Message` (common/Message.java)**:
    *   클라이언트와 서버 간에 교환되는 모든 데이터의 공통 형식입니다.
    *   메시지 유형(`MessageType`), 성공/실패 여부(`isSuccess`), 오류 코드(`ErrorCode`), 데이터(`getData()`), 전송자/수신자 ID, 방 정보 등을 포함하여 다양한 종류의 통신을 처리합니다.
    *   `common` 패키지에 정의되어 클라이언트와 서버 모두에서 공유됩니다.
*   **`UIHelper` (client/util/UIHelper.java)**:
    *   토스트 메시지 표시(`showToast`), 비밀번호 입력 다이얼로그(`showPasswordDialog`), 게임 정답 숫자 입력 및 유효성 검사(`promptForAnswerKey`, `isValidAnswerKey`, `isValidGuess`), 배경 패널 생성(`createBackgroundPanel`) 등 공통적으로 사용되는 정적(static) UI 유틸리티 메서드를 제공합니다.
*   **`PlayerListCellRenderer` (client/ui/PlayerListCellRenderer.java)**:
    *   `RoomWaitingPanel`의 플레이어 목록(`JList`)을 커스터마이징하여 각 플레이어의 ID, 방장 여부, 준비 상태를 색상과 텍스트로 시각화합니다.
*   **`UserListCellRenderer` (client/ui/UserListCellRenderer.java)**:
    *   `LobbyPanel`의 접속자 목록(`JList`)을 커스터마이징하여 각 사용자의 ID와 온라인/방 참여 중/게임 중 상태를 작은 아이콘과 색상으로 표시합니다.
*   **`PredictionEntry` (client/ui/PredictionEntry.java)**:
    *   `GamePanel`에서 게임 중 자신의 예측 내역(추측 숫자, 스트라이크, 볼 개수)을 저장하기 위한 간단한 데이터 클래스입니다.

### 3. 시작부터 종료까지의 실행 흐름

1.  **애플리케이션 시작**:
    *   `BaseballClientGUI` 클래스의 `main` 메서드가 실행되어 `BaseballClientGUI` 인스턴스를 생성합니다.
2.  **초기화**:
    *   `BaseballClientGUI` 생성자에서 `GameStateManager.getInstance()`를 통해 싱글톤 `GameStateManager`를 초기화하고, `new NetworkManager(this)`를 호출하여 `NetworkManager`를 초기화합니다. `BaseballClientGUI`는 `MessageHandler` 인터페이스를 구현하므로 `NetworkManager`에 자신의 인스턴스를 전달합니다.
    *   `buildGUI()` 메서드에서는 `CardLayout` 기반의 `mainPanel`을 설정하고, `LoginPanel`, `LobbyPanel` 등 모든 UI 패널 인스턴스를 생성하여 `mainPanel`에 추가합니다. 이때 각 패널의 생성자에는 `BaseballClientGUI` 인스턴스(각 패널의 리스너 역할)가 전달됩니다.
    *   초기 화면으로 `LoginPanel`을 표시합니다.
3.  **로그인/회원가입**:
    *   사용자가 `LoginPanel`에서 로그인 또는 회원가입 정보를 입력하고 버튼을 클릭합니다.
    *   `LoginPanel`은 자신의 리스너(`LoginListener`)를 통해 `BaseballClientGUI`의 `onLoginRequested` 또는 `onRegisterRequested` 메서드를 호출합니다.
    *   `BaseballClientGUI`는 `NetworkManager.connect()`를 호출하여 서버에 연결을 시도하고, `Message.createLoginRequest()` 또는 `Message.createRegisterRequest()`를 통해 적절한 `Message` 객체를 생성한 뒤 `networkManager.sendMessage()`를 통해 서버에 요청을 보냅니다. 동시에 `stateManager`에 현재 사용자 ID와 비밀번호를 저장합니다.
4.  **네트워크 통신 및 메시지 처리**:
    *   `NetworkManager`는 서버에 연결하고(필요한 경우), `Message` 객체를 직렬화하여 전송합니다.
    *   `NetworkManager` 내부의 `receiveThread`는 서버로부터 메시지를 `in.readObject()`를 통해 계속해서 읽어 들입니다.
    *   메시지가 수신되면 `receiveThread`는 `SwingUtilities.invokeLater()`를 사용하여 UI 스레드(Event Dispatch Thread, EDT)에서 `BaseballClientGUI`의 `handleMessage(Message msg)` 메서드를 호출합니다. 이는 Swing UI 컴포넌트가 단일 스레드에서만 접근되어야 하는 규칙을 준수하기 위함입니다.
    *   `handleMessage`는 메시지 유형(`msg.getType()`)에 따라 `handleLoginResponse`, `handleRoomListResponse` 등 적절한 처리 메서드를 호출하여 서버 응답을 처리합니다.
5.  **상태 업데이트 및 화면 전환**:
    *   `handleMessage`의 하위 처리 메서드들은 서버 응답에 따라 `GameStateManager`의 상태를 업데이트합니다. 예를 들어, 로그인 성공 시 `stateManager.setAuthenticated(true, ...)`를 호출합니다.
    *   상태 업데이트 후 `switchToLobbyScreen()`, `switchToRoomWaitingScreen()` 등을 호출하여 `CardLayout`의 `show()` 메서드를 통해 `LobbyPanel`이나 `RoomWaitingPanel` 등으로 화면을 전환합니다.
    *   `switchToLobbyScreen()`에서는 `lobbyPanel.updateUserInfo()`, `lobbyPanel.updateRoomList()` 등을 호출하고 `USER_LIST_REQUEST` 메시지를 보내 접속자 목록을 요청합니다.
6.  **방 생성 및 입장**:
    *   `LobbyPanel`에서 사용자가 "방 생성" 또는 "방 입장" 버튼을 클릭하면, `LobbyListener`를 통해 `BaseballClientGUI`의 `onCreateRoomRequested` 또는 `onJoinRoomRequested` 메서드가 호출됩니다.
    *   `BaseballClientGUI`는 `Message` 객체를 생성하여 서버에 전송합니다.
    *   서버 응답(`CREATE_ROOM_RESPONSE`, `JOIN_ROOM_RESPONSE`)이 오면 `BaseballClientGUI`의 해당 핸들러(`handleCreateRoomResponse`, `handleJoinRoomResponse`)가 `GameStateManager`의 방 관련 상태를 업데이트하고 `switchToRoomWaitingScreen()`을 호출합니다.
    *   `RoomWaitingPanel`은 `updateRoomInfo()`와 `updatePlayerList()`를 호출하여 `GameStateManager`의 정보를 바탕으로 UI를 갱신합니다.
7.  **게임 진행**:
    *   `RoomWaitingPanel`에서 방장이 "게임 시작" 버튼을 클릭하면 `onStartGameRequested`를 통해 서버에 `START_GAME_REQUEST` 메시지를 보냅니다.
    *   서버로부터 `START_GAME` 메시지가 오면 `BaseballClientGUI`의 `handleStartGame` 메서드가 호출되어 `stateManager`에 게임 ID, 난이도, 턴 제한 시간을 저장하고 `gamePanel.setupForGame()`을 호출하여 UI를 게임에 맞게 설정한 뒤 `switchToGameScreen()`을 호출합니다.
    *   사용자는 `UIHelper.promptForAnswerKey()`를 통해 자신의 정답 숫자를 입력하고, 이 정답은 `stateManager.setMyAnswerKey()`를 통해 저장된 후 서버로 전송됩니다.
    *   `GamePanel`에서는 사용자가 숫자를 선택하여 추측을 제출합니다. `onGuessSubmitted`를 통해 `BaseballClientGUI`로 전달되고, `BaseballClientGUI`는 `Message.createGuessMessage()`를 생성하여 서버에 보냅니다.
    *   서버로부터 `TURN_INFO`, `GUESS_RESULT` 메시지가 오면 `BaseballClientGUI`가 이를 처리하여 `GamePanel`의 턴 정보(`updateTurnInfo`), 타이머(`updateTimer`), 예측 기록(`addPrediction`) 등을 업데이트합니다. `TURN_INFO` 메시지 수신 시 `GameStateManager`에 저장된 턴 타이머(`turnTimer`)가 시작됩니다.
8.  **채팅**:
    *   `LobbyPanel`, `RoomWaitingPanel`, `GamePanel` 등에서 채팅 메시지를 입력하면 해당 리스너(`onLobbyChatSent`, `onRoomChatSent`, `onChatSent`)를 통해 `BaseballClientGUI`로 전달됩니다.
    *   `BaseballClientGUI`는 메시지 타입(`CHAT_ALL`, `CHAT_ROOM`, `CHAT_WHISPER`, `CHAT_TEAM`)에 따라 `Message` 객체를 생성하여 `NetworkManager`를 통해 서버에 전송합니다.
    *   서버로부터 채팅 메시지(`CHAT_ALL`, `CHAT_ROOM`, `CHAT_WHISPER`, `CHAT_TEAM`)가 수신되면, `BaseballClientGUI`의 `handleChatAll`, `handleChatRoom`, `handleWhisper` 등의 메서드가 현재 화면(`currentState`)에 따라 적절한 UI 패널의 `addChatMessage` 또는 `displayMessage` 메서드를 호출하여 메시지를 표시합니다.
9.  **게임 종료**:
    *   서버로부터 `END_GAME` 또는 `GAME_RESULT` 메시지가 오면 `handleGameResult` 메서드가 호출됩니다.
    *   `GameStateManager`의 `turnTimer`가 중지되고, `ResultPanel.setResult()`를 호출하여 승패 정보를 설정한 뒤 `switchToResultScreen()`을 호출합니다. `ResultPanel`은 결과에 따라 다른 배경 이미지를 표시합니다.
    *   사용자는 `ResultPanel`에서 "방에 남기"(`onStayInRoom`) 또는 "로비로 나가기"(`onLeaveToLobby`)를 선택하여 다음 행동을 결정합니다. "로비로 나가기" 선택 시 `GameStateManager.resetRoomState()`가 호출되고 `switchToLobbyScreen()`으로 화면이 전환됩니다.
10. **애플리케이션 종료**:
    *   사용자가 "Exit" 버튼을 클릭하거나 창을 닫으면 `BaseballClientGUI`의 `onExitRequested` (또는 `onDisconnectRequested`) 메서드가 호출됩니다.
    *   이 메서드는 `NetworkManager.disconnect()`를 호출하여 서버와의 연결을 종료하고, `System.exit(0)`를 통해 애플리케이션을 완전히 종료합니다.

### 4. 모듈 간 상호작용 방식

*   **`BaseballClientGUI` (중앙 컨트롤러)**:
    *   **`NetworkManager`와의 상호작용**: `networkManager.sendMessage(msg)`를 통해 서버로 메시지를 보내고, `MessageHandler` 인터페이스의 `handleMessage(msg)` 메서드 구현을 통해 서버로부터 메시지를 수신하여 처리합니다.
    *   **`GameStateManager`와의 상호작용**: 게임 상태를 업데이트(`stateManager.setCurrentRoomId(...)`)하거나, UI 렌더링에 필요한 상태를 조회(`stateManager.getCurrentUserId()`)합니다.
    *   **UI 패널과의 상호작용**: 각 UI 패널을 생성할 때 `this` (`BaseballClientGUI` 자신)를 리스너로 등록하여 패널에서 발생하는 사용자 이벤트를 수신합니다. 서버 응답이나 사용자 액션에 따라 `cardLayout.show()`를 통해 화면을 전환하고, 필요 시 패널의 업데이트 메서드를 직접 호출합니다(예: `lobbyPanel.updateRoomList()`).
*   **UI 패널 (`LoginPanel`, `LobbyPanel` 등)**:
    *   사용자 입력(버튼 클릭, 텍스트 입력)을 받으면, 자신에게 등록된 리스너 인터페이스(예: `LoginListener`, `LobbyListener`)의 메서드를 호출하여 `BaseballClientGUI`에 알립니다.
    *   `GameStateManager.getInstance()`를 통해 싱글톤 `GameStateManager`에서 직접 데이터를 가져와 UI를 업데이트하기도 합니다(예: `RoomWaitingPanel`이 `stateManager`에서 플레이어 목록과 방 정보를 가져와 표시).
*   **`NetworkManager`**:
    *   서버와의 저수준 통신을 처리하고, 수신된 메시지를 `MessageHandler` 인터페이스를 통해 `BaseballClientGUI`로 전달합니다. `NetworkManager` 자체는 `GameStateManager`나 UI 패널과 직접 상호작용하지 않습니다.
*   **`GameStateManager`**:
    *   다른 모듈(주로 `BaseballClientGUI`)로부터 상태 업데이트 요청을 받거나, 다른 모듈에게 상태 정보를 제공합니다. `GameStateManager`는 UI나 네트워크 로직을 직접 수행하지 않고, 순수하게 데이터와 그 관리에만 집중합니다.
*   **리스너 인터페이스**:
    *   UI 패널과 `BaseballClientGUI` 간의 느슨한 결합을 제공합니다. 패널은 특정 이벤트가 발생했음을 리스너에게 알리고, 리스너(여기서는 `BaseballClientGUI`)는 해당 이벤트를 처리하는 로직을 수행합니다.

### 5. 키 클래스 및 역할 (핵심 요약)

*   **`BaseballClientGUI`**: 애플리케이션의 허브. UI 흐름, 이벤트 처리, 서버 메시지 디스패치 담당.
*   **`NetworkManager`**: 서버와의 연결, `common.Message` 객체 송수신 등 네트워크 통신 계층 담당. 비동기 메시지 수신 및 UI 스레드 안전성 확보.
*   **`GameStateManager`**: 클라이언트 애플리케이션의 모든 동적 상태(인증, 방, 게임)를 중앙에서 관리하는 싱글톤 데이터 모델.
*   **`Message`**: 클라이언트-서버 통신을 위한 표준 메시지 객체 정의.
*   **UI 패널 클래스 (예: `LoginPanel`, `LobbyPanel`, `GamePanel`)**: 특정 화면의 시각적 표현 및 사용자 상호작용 처리.
*   **리스너 인터페이스 (예: `LoginListener`, `GamePanelListener`)**: UI 이벤트와 컨트롤러(`BaseballClientGUI`) 간의 통신 규약 정의.
*   **`UIHelper`**: 공통 UI 작업(토스트, 다이얼로그, 입력 유효성 검사 등)을 위한 정적 유틸리티 메서드 제공.

### 6. 상태 관리 접근 방식

이 애플리케이션은 **싱글톤 `GameStateManager`**를 통해 애플리케이션 전체의 상태를 중앙 집중식으로 관리합니다.

*   **중앙 집중화**: 사용자 인증 정보, 현재 방 정보, 게임 진행 상태, 플레이어 목록, 준비 상태, 게임 설정, 턴 타이머 등 모든 핵심 상태가 `GameStateManager` 인스턴스 하나에 모여 있습니다.
*   **접근 및 업데이트**:
    *   `BaseballClientGUI`는 서버로부터 메시지를 수신하면, 해당 메시지에 포함된 최신 정보를 바탕으로 `GameStateManager`의 관련 필드들을 업데이트합니다. 예를 들어, 로그인 성공 시 `setAuthenticated(true, userId, password)`를 호출하고, 방 정보 업데이트 시 `setCurrentRoomId`, `setRoomMasterUserId` 등을 호출합니다.
    *   UI 패널들은 `GameStateManager.getInstance()`를 통해 싱글톤 인스턴스에 접근하여 현재 상태를 조회하고, 이를 바탕으로 자신들의 UI를 렌더링하거나 업데이트합니다. 예를 들어, `RoomWaitingPanel`은 `updateRoomInfo()`나 `updatePlayerList()` 메서드 내에서 `stateManager`의 정보를 읽어와 화면에 표시합니다.
*   **상태 리셋**: `GameStateManager`는 `resetAllState()`, `resetRoomState()`, `resetGameState()`와 같은 명확한 상태 초기화 메서드를 제공하여 특정 시점(로그아웃, 방 퇴장, 게임 종료)에 관련된 상태를 안전하게 초기화할 수 있도록 합니다.
*   **UI 업데이트 트리거**: `GameStateManager`의 상태가 변경되면, `BaseballClientGUI`는 해당 변경 사항을 반영하기 위해 적절한 UI 패널의 업데이트 메서드를 호출하거나, 화면을 전환하여 새로운 상태를 사용자에게 보여줍니다.

### 7. 네트워크 통신 처리

네트워크 통신은 전적으로 `NetworkManager` 클래스에 의해 처리됩니다.

*   **연결 정보 로드**: `CONN_INFO.txt` 파일이 아닌 `server.txt` 파일에서 서버의 IP 주소와 포트 번호를 읽어와 사용합니다. 파일이 없거나 잘못된 경우 기본값(`localhost:54321`)을 사용합니다.
*   **TCP 소켓 통신**: `java.net.Socket`을 사용하여 서버와 TCP 연결을 설정합니다.
*   **객체 직렬화/역직렬화**: `ObjectOutputStream`과 `ObjectInputStream`을 사용하여 클라이언트와 서버 간에 `common.Message` 객체를 직접 전송하고 수신합니다. 이는 복잡한 데이터 구조를 쉽게 교환할 수 있게 하며, 메시지 타입(`MessageType`)을 통해 다양한 통신 목적을 식별합니다.
*   **비동기 메시지 수신**: `receiveMessage()` 메서드는 별도의 `receiveThread`에서 실행되어 서버로부터의 메시지를 `in.readObject()`를 통해 끊임없이 대기합니다. 이 스레드는 네트워크 I/O 작업을 담당하며, GUI 스레드가 블로킹되는 것을 방지하여 애플리케이션의 응답성을 유지합니다.
*   **UI 스레드 안전성**: `receiveThread`에서 서버 메시지를 수신한 후, `SwingUtilities.invokeLater()`를 사용하여 `BaseballClientGUI`의 `handleMessage()` 메서드를 호출합니다. 이는 모든 Swing UI 컴포넌트 조작이 Event Dispatch Thread (EDT)에서 이루어져야 하는 Swing의 규칙을 지키기 위함입니다.
*   **오류 처리**: 연결 실패(`IOException`) 또는 메시지 처리 중 오류 발생 시(`ClassNotFoundException`, 기타 `Exception`) 적절한 오류 메시지를 `JOptionPane` 또는 `UIHelper.showToast()`를 통해 사용자에게 표시하고, 필요한 경우 연결을 종료하거나 로그인 화면으로 돌아가는 등의 조치를 취합니다. 예를 들어 `ALREADY_LOGGED_IN` 또는 `LOGIN_FAILED` 오류 발생 시 `networkManager.disconnect()` 후 로그인 화면으로 전환합니다.
*   **메시지 전송**: `sendMessage(Message msg)` 메서드를 통해 `BaseballClientGUI`에서 생성된 `Message` 객체를 `out.writeObject()` 및 `out.flush()`를 사용하여 서버로 보냅니다.