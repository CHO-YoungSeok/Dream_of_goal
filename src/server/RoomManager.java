package server;

import common.Message;
import java.util.Vector;

// 방 관리 클래스

public class RoomManager {
    private final Vector<GameRoom> rooms = new Vector<>();
    private int nextRoomId = 1;
    private final int maxRooms = 5;

    private ServerCore serverCore;

    public RoomManager(ServerCore serverCore) {
        this.serverCore = serverCore;
    }

    /**
     * 방 생성
     */
    public GameRoom createRoom(String roomName, String masterUserId,
                               Message.GameMode gameMode, Message.Difficulty difficulty,
                               Message.TurnTimeLimit turnTimeLimit,
                               boolean isPrivate, String roomPassword) {

        if (rooms.size() >= maxRooms) {
            return null;
        }

        GameRoom room = new GameRoom(nextRoomId++, roomName, masterUserId,
                gameMode, difficulty, turnTimeLimit,
                isPrivate, roomPassword, serverCore);
        rooms.add(room);
        serverCore.printDisplay("방 생성: [" + room.roomId + "] " + roomName);
        return room;
    }

    /**
     * 방 찾기
     */
    public GameRoom findRoom(int roomId) {
        for (GameRoom room : rooms) {
            if (room.roomId == roomId) {
                return room;
            }
        }
        return null;
    }

    /**
     * 방 삭제
     */
    public void removeRoom(GameRoom room) {
        rooms.remove(room);
        serverCore.printDisplay("방 삭제: [" + room.roomId + "]");
    }

    /**
     * 방 정보 변경
     */
    public GameRoom editRoom(GameRoom oldRoom, String roomName,
                             Message.Difficulty difficulty,
                             Message.TurnTimeLimit turnTimeLimit,
                             boolean isPrivate, String roomPassword) {

        if (oldRoom.isGameRunning) {
            return null;
        }

        // 기존 플레이어 정보 저장
        Vector<ClientHandler> oldPlayers = new Vector<>(oldRoom.players);
        String roomMaster = oldRoom.roomMaster;
        Message.GameMode gameMode = oldRoom.gameMode;

        // 기존 방 삭제
        removeRoom(oldRoom);

        // 새 방 생성
        GameRoom newRoom = new GameRoom(nextRoomId++, roomName, roomMaster,
                gameMode, difficulty, turnTimeLimit,
                isPrivate, roomPassword, serverCore);
        rooms.add(newRoom);
        serverCore.printDisplay("방 정보 변경: [" + newRoom.roomId + "] " + roomName);

        // 플레이어 이동
        for (ClientHandler player : oldPlayers) {
            player.currentRoom = newRoom;
            newRoom.players.add(player);

            if (player.userId.equals(roomMaster)) {
                newRoom.readyStatus.put(player.userId, true);
            } else {
                newRoom.readyStatus.put(player.userId, false);
            }

            // 2v2 팀 재배정
            if (gameMode == Message.GameMode.TWO_VS_TWO) {
                int teamNum = (newRoom.players.size() <= 2) ? 1 : 2;
                newRoom.playerTeams.put(player.userId, teamNum);
            }
        }

        return newRoom;
    }

    /**
     * 방 목록 조회
     */
    public Vector<Message> getRoomList() {
        Vector<Message> roomList = new Vector<>();

        for (GameRoom room : rooms) {
            Message roomInfo = new Message(Message.MessageType.ROOM_LIST_RESPONSE, room.roomMaster);
            roomInfo.setRoomId(room.roomId);
            roomInfo.setRoomName(room.roomName + (room.isPrivate ? " 🔒" : ""));
            roomInfo.setRoomStatus(room.isGameRunning ?
                    Message.RoomStatus.IN_GAME :
                    Message.RoomStatus.WAITING);
            roomInfo.setCurrentPlayers(room.players.size());
            roomInfo.setMaxPlayers(room.gameMode.getMaxPlayers());
            roomInfo.setGameMode(room.gameMode);
            roomInfo.setDifficulty(room.difficulty);
            roomInfo.setRoomMaster(room.roomMaster);
            roomInfo.setPrivate(room.isPrivate);

            roomList.add(roomInfo);
        }

        return roomList;
    }

    /**
     * 전체 방 개수
     */
    public int getRoomCount() {
        return rooms.size();
    }

    /**
     * 최대 방 개수
     */
    public int getMaxRooms() {
        return maxRooms;
    }
}