클라이언트 코드 구조 및 흐름에 대한 분석은 다음과 같습니다.

### 1. 전체 아키텍처 및 조직

이 클라이언트 애플리케이션은 클라이언트-서버 아키텍처를 기반으로 하며, GUI는 Java Swing을 사용하여 구현되었습니다. 전체적으로 MVC(Model-View-Controller) 패턴과 유사한 형태로 구성되어 있습니다:

*   **View (UI Panels):** `LoginPanel`, `LobbyPanel`, `RoomWaitingPanel`, `GamePanel`, `ResultPanel` 등 개별 화면을 담당하는 JPanel 서브클래스들.
*   **Model (GameStateManager):** 애플리케이션의 모든 상태(사용자 정보, 방 정보, 게임 정보 등)를 중앙 집중적으로 관리하는 싱글톤 클래스.
*   **Controller (BaseballClientGUI):** 메인 프레임이자 모든 UI 패널의 이벤트를 수신하고, 서버로부터의 메시지를 처리하며, `GameStateManager`를 업데이트하고 화면 전환을 조정하는 중앙 컨트롤러 역할을 합니다.

각 UI 구성 요소, 네트워크 통신, 상태 관리 등 각기 다른 책임 영역을 명확하게 분리하여 모듈성을 높였습니다. 또한, UI 이벤트 처리를 위해 리스너 패턴(Listener Pattern)을 적극적으로 활용합니다.

### 2. 주요 구성 요소 및 역할

*   **`BaseballClientGUI`**:
    *   애플리케이션의 메인 창 (JFrame).
    *   `CardLayout`을 사용하여 여러 UI 패널(화면)을 전환합니다.
    *   `MessageHandler` 인터페이스를 구현하여 `NetworkManager`로부터 서버 메시지를 수신합니다.
    *   각 UI 패널의 리스너 인터페이스(`LoginListener`, `LobbyListener`, `RoomWaitingListener`, `GamePanelListener`, `ResultPanelListener`)를 구현하여 사용자 이벤트를 처리합니다.
    *   `NetworkManager`와 `GameStateManager` 인스턴스를 생성하고 관리합니다.
    *   서버 응답이나 사용자 동작에 따라 UI 화면을 전환하고 상태를 업데이트하는 중앙 조정자 역할을 합니다.
*   **`NetworkManager`**:
    *   서버와의 모든 네트워크 통신을 관리합니다.
    *   `CONN_INFO.txt` 파일에서 서버 주소와 포트 정보를 로드합니다.
    *   소켓 연결, 메시지 송수신(Object Serialization 이용)을 담당합니다.
    *   서버로부터 메시지를 비동기적으로 수신하기 위해 별도의 스레드(`receiveThread`)를 사용합니다.
    *   수신된 메시지를 `MessageHandler` 인터페이스를 통해 `BaseballClientGUI`로 전달합니다.
*   **`GameStateManager`**:
    *   애플리케이션의 모든 중요한 상태를 저장하고 관리하는 싱글톤 클래스입니다.
    *   현재 로그인된 사용자 정보, 현재 참여 중인 방의 정보(ID, 이름, 방장, 게임 모드, 난이도 등), 방에 있는 플레이어 목록 및 준비 상태, 현재 게임의 진행 상태(라운드, 턴 플레이어, 나의 정답 등)를 포함합니다.
    *   `BaseballClientGUI` 및 다른 UI 패널들이 이 `GameStateManager`에 접근하여 상태를 읽거나 업데이트합니다.
*   **UI 패널 (`LoginPanel`, `LobbyPanel`, `RoomWaitingPanel`, `GamePanel`, `ResultPanel`)**:
    *   특정 화면의 사용자 인터페이스를 렌더링하고 사용자 입력을 받습니다.
    *   자신에게 발생한 사용자 이벤트를 해당 리스너 인터페이스(`LoginListener`, `LobbyListener` 등)를 통해 `BaseballClientGUI`에 알립니다.
    *   일부 패널은 `GameStateManager`에서 직접 상태를 읽어와 UI를 업데이트합니다(예: `RoomWaitingPanel`, `GamePanel`).
    *   `UIHelper` 유틸리티 클래스를 사용하여 공통적인 UI 작업을 처리합니다.
*   **리스너 인터페이스 (`LoginListener`, `LobbyListener`, `RoomWaitingListener`, `GamePanelListener`, `ResultPanelListener`)**:
    *   각 UI 패널에서 발생하는 사용자 이벤트에 대한 콜백 메서드를 정의합니다.
    *   `BaseballClientGUI`가 이 인터페이스들을 구현하여 각 패널의 이벤트를 수신하고 처리합니다.
*   **`Message`**:
    *   클라이언트와 서버 간에 교환되는 모든 데이터의 공통 형식입니다.
    *   메시지 유형(`MessageType`), 성공/실패 여부, 오류 코드(`ErrorCode`), 데이터 등을 포함합니다.
    *   `common` 패키지에 정의되어 클라이언트와 서버 모두에서 공유됩니다.
*   **`UIHelper`**:
    *   토스트 메시지 표시, 비밀번호 입력 다이얼로그, 게임 정답 숫자 입력 및 유효성 검사, 배경 패널 생성 등 공통적으로 사용되는 UI 유틸리티 메서드를 제공하는 정적(static) 유틸리티 클래스입니다.

### 3. 시작부터 종료까지의 실행 흐름

1.  **시작 (`main` 메서드)**: `BaseballClientGUI` 클래스의 `main` 메서드가 실행되어 `BaseballClientGUI` 인스턴스를 생성합니다.
2.  **초기화**:
    *   `BaseballClientGUI` 생성자에서 `GameStateManager` (싱글톤)와 `NetworkManager`를 초기화합니다.
    *   `buildGUI()` 메서드를 호출하여 `CardLayout` 기반의 `mainPanel`을 설정하고, `LoginPanel`, `LobbyPanel` 등 모든 UI 패널을 생성하여 `mainPanel`에 추가합니다.
    *   초기 화면으로 `LoginPanel`을 표시합니다.
3.  **로그인/회원가입**:
    *   사용자가 `LoginPanel`에서 로그인 또는 회원가입 정보를 입력하고 버튼을 클릭합니다.
    *   `LoginPanel`은 자신의 리스너(`LoginListener`)를 통해 `BaseballClientGUI`의 `onLoginRequested` 또는 `onRegisterRequested` 메서드를 호출합니다.
    *   `BaseballClientGUI`는 `NetworkManager`를 통해 서버에 해당 요청 메시지(`Message.MessageType.LOGIN_REQUEST`, `REGISTER_REQUEST`)를 보냅니다.
4.  **네트워크 통신 및 메시지 처리**:
    *   `NetworkManager`는 서버에 연결하고(필요한 경우), 메시지를 직렬화하여 전송합니다.
    *   `NetworkManager` 내부의 `receiveThread`는 서버로부터 메시지를 계속해서 읽어 들입니다.
    *   메시지가 수신되면 `receiveThread`는 `SwingUtilities.invokeLater()`를 사용하여 UI 스레드에서 `BaseballClientGUI`의 `handleMessage(Message msg)` 메서드를 호출합니다.
    *   `handleMessage`는 메시지 유형에 따라 적절한 처리 메서드(`handleLoginResponse`, `handleRoomListResponse` 등)를 호출하여 서버 응답을 처리합니다.
5.  **상태 업데이트 및 화면 전환**:
    *   `handleMessage`의 하위 메서드들은 서버 응답에 따라 `GameStateManager`의 상태를 업데이트합니다.
    *   예를 들어, 로그인 성공 시 `stateManager.setAuthenticated(true, ...)`를 호출하고 `switchToLobbyScreen()`을 통해 `LobbyPanel`로 화면을 전환합니다.
    *   방 생성, 방 참가, 게임 시작, 턴 정보 업데이트, 게임 결과 등의 과정에서 지속적으로 `GameStateManager`가 업데이트되고 해당 UI 패널이 새로 고쳐지거나 화면이 전환됩니다.
6.  **게임 진행**:
    *   `GamePanel`에서는 사용자가 숫자를 선택하고 추측을 제출합니다.
    *   `GamePanel`은 리스너를 통해 `BaseballClientGUI`의 `onGuessSubmitted`를 호출하고, `BaseballClientGUI`는 `NetworkManager`를 통해 서버에 추측 메시지를 보냅니다.
    *   서버로부터 `TURN_INFO`, `GUESS_RESULT`, `END_GAME`, `GAME_RESULT` 등의 메시지가 오면 `BaseballClientGUI`가 이를 처리하여 `GamePanel`이나 `ResultPanel`의 UI를 업데이트합니다.
7.  **종료**:
    *   사용자가 "Exit" 버튼을 클릭하거나 창을 닫으면 `BaseballClientGUI`의 `onExitRequested` (또는 `onDisconnectRequested`) 메서드가 호출됩니다.
    *   이 메서드는 `NetworkManager.disconnect()`를 호출하여 서버와의 연결을 종료하고, `System.exit(0)`를 통해 애플리케이션을 종료합니다.

### 4. 모듈 간 상호작용 방식

*   **`BaseballClientGUI` (중앙 컨트롤러)**:
    *   **`NetworkManager`와 상호작용**: 메시지를 서버로 보내고(`networkManager.sendMessage()`), 서버로부터 메시지를 수신하여 처리합니다(`handleMessage()` 구현).
    *   **`GameStateManager`와 상호작용**: 게임 상태를 업데이트하고(`stateManager.setCurrentRoomId(...)`), UI 렌더링에 필요한 상태를 조회합니다(`stateManager.getCurrentUserId()`).
    *   **UI 패널과 상호작용**: 각 UI 패널을 생성할 때 `this` (`BaseballClientGUI` 자신)를 리스너로 등록하여 패널에서 발생하는 이벤트를 수신합니다. 서버 응답이나 사용자 액션에 따라 `cardLayout.show()`를 통해 화면을 전환하고, 필요 시 패널의 업데이트 메서드를 직접 호출합니다(예: `lobbyPanel.updateRoomList()`).
*   **UI 패널**:
    *   사용자 입력(버튼 클릭, 텍스트 입력)을 받으면, 자신에게 등록된 리스너 인터페이스(예: `LoginListener`, `LobbyListener`)의 메서드를 호출하여 `BaseballClientGUI`에 알립니다.
    *   `GameStateManager`에서 직접 데이터를 가져와 UI를 업데이트하기도 합니다(예: `RoomWaitingPanel`이 `stateManager`에서 플레이어 목록과 방 정보를 가져옴).
*   **`NetworkManager`**:
    *   서버와의 저수준 통신을 처리하고, 수신된 메시지를 `MessageHandler` 인터페이스를 통해 `BaseballClientGUI`로 전달합니다. `NetworkManager` 자체는 `GameStateManager`나 UI 패널과 직접 상호작용하지 않습니다.
*   **`GameStateManager`**:
    *   다른 모듈로부터 상태 업데이트 요청을 받거나(주로 `BaseballClientGUI`에서), 다른 모듈에게 상태 정보를 제공합니다. `GameStateManager`는 UI나 네트워크 로직을 직접 수행하지 않고, 순수하게 데이터와 그 관리에만 집중합니다.
*   **리스너 인터페이스**:
    *   UI 패널과 `BaseballClientGUI` 간의 느슨한 결합을 제공합니다. 패널은 특정 이벤트가 발생했음을 리스너에게 알리고, 리스너(여기서는 `BaseballClientGUI`)는 해당 이벤트를 처리하는 로직을 수행합니다.

### 5. 키 클래스 및 역할 (핵심 요약)

*   **`BaseballClientGUI`**: 애플리케이션의 허브. UI 흐름, 이벤트 처리, 서버 메시지 디스패치 담당.
*   **`NetworkManager`**: 서버와의 연결, 메시지 송수신 등 네트워크 통신 계층 담당.
*   **`GameStateManager`**: 클라이언트 애플리케이션의 모든 동적 상태를 중앙에서 관리하는 데이터 모델.
*   **`Message`**: 클라이언트-서버 통신을 위한 표준 메시지 객체 정의.
*   **UI 패널 클래스 (예: `LoginPanel`, `LobbyPanel`, `GamePanel`)**: 특정 화면의 시각적 표현 및 사용자 상호작용 처리.
*   **리스너 인터페이스 (예: `LoginListener`, `GamePanelListener`)**: UI 이벤트와 컨트롤러(`BaseballClientGUI`) 간의 통신 규약 정의.

### 6. 상태 관리 접근 방식

이 애플리케이션은 **싱글톤 `GameStateManager`**를 통해 애플리케이션 전체의 상태를 관리합니다.

*   **중앙 집중화**: 사용자 인증 정보, 현재 방 정보, 게임 진행 상태, 플레이어 목록, 준비 상태, 게임 설정 등 모든 핵심 상태가 `GameStateManager` 인스턴스 하나에 모여 있습니다.
*   **접근 및 업데이트**:
    *   `BaseballClientGUI`는 서버로부터 메시지를 수신하면, 해당 메시지에 포함된 최신 정보를 바탕으로 `GameStateManager`의 관련 필드들을 업데이트합니다.
    *   UI 패널들은 `GameStateManager.getInstance()`를 통해 싱글톤 인스턴스에 접근하여 현재 상태를 조회하고, 이를 바탕으로 자신들의 UI를 렌더링하거나 업데이트합니다.
    *   예를 들어, `RoomWaitingPanel`은 `updateRoomInfo()`나 `updatePlayerList()` 메서드 내에서 `stateManager`의 정보를 읽어와 화면에 표시합니다.
*   **상태 리셋**: `resetAllState()`, `resetRoomState()`, `resetGameState()`와 같은 메서드를 제공하여 특정 시점(로그아웃, 방 퇴장, 게임 종료)에 관련된 상태를 초기화할 수 있습니다.
*   **UI 업데이트 트리거**: `GameStateManager`의 상태가 변경되면, `BaseballClientGUI`는 해당 변경 사항을 반영하기 위해 적절한 UI 패널의 업데이트 메서드를 호출하거나, 화면을 전환하여 새로운 상태를 사용자에게 보여줍니다.

### 7. 네트워크 통신 처리

네트워크 통신은 전적으로 `NetworkManager` 클래스에 의해 처리됩니다.

*   **연결 정보 로드**: `CONN_INFO.txt` 파일에서 서버의 IP 주소와 포트 번호를 읽어와 사용합니다. 파일이 없거나 잘못된 경우 기본값(`localhost:54321`)을 사용합니다.
*   **TCP 소켓 통신**: `java.net.Socket`을 사용하여 서버와 TCP 연결을 설정합니다.
*   **객체 직렬화/역직렬화**: `ObjectOutputStream`과 `ObjectInputStream`을 사용하여 클라이언트와 서버 간에 `common.Message` 객체를 직접 전송하고 수신합니다. 이는 복잡한 데이터 구조를 쉽게 교환할 수 있게 합니다.
*   **비동기 메시지 수신**: `receiveMessage()` 메서드는 별도의 `receiveThread`에서 실행되어 서버로부터의 메시지를 끊임없이 대기합니다. 이는 GUI 스레드가 네트워크 작업으로 인해 블로킹되는 것을 방지하여 애플리케이션의 응답성을 유지합니다.
*   **UI 스레드 안전성**: `receiveThread`에서 서버 메시지를 수신한 후, `SwingUtilities.invokeLater()`를 사용하여 `BaseballClientGUI`의 `handleMessage()` 메서드를 호출합니다. 이는 Swing UI 컴포넌트가 단일 스레드(Event Dispatch Thread, EDT)에서만 접근되어야 하는 규칙을 지키기 위함입니다.
*   **오류 처리**: 연결 실패(`IOException`) 또는 메시지 처리 중 오류 발생 시(`ClassNotFoundException`, 기타 `Exception`) 적절한 오류 메시지를 사용자에게 표시하고, 필요한 경우 연결을 종료하거나 로그인 화면으로 돌아가는 등의 조치를 취합니다.
*   **메시지 전송**: `sendMessage(Message msg)` 메서드를 통해 `BaseballClientGUI`에서 생성된 `Message` 객체를 서버로 보냅니다.