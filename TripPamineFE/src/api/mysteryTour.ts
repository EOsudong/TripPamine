import axios from "axios";

export interface MysteryTourRequest {
  travelDate: string;
  travelDays: number;
  peopleCount: number;
  budget: number;
  radiusKm: number;
  departure: string;
  travelStyle: string;
}

export interface MysteryTourResponse {
  mysteryTourId: number;
  travelDate: string;
  travelDays: number;
  peopleCount: number;
  budget: number;
  questCount: number;
  status: string;
  destinationLocked: boolean;
}

export const createMysteryTour = async (
  data: MysteryTourRequest
): Promise<MysteryTourResponse> => {

  const userId = localStorage.getItem("userId");
  const token = localStorage.getItem("accessToken");

  if (!userId) {
    throw new Error("로그인 사용자 정보를 찾을 수 없습니다.");
  }

  const response = await axios.post(
    `http://localhost:8080/mystery-tours?userId=${userId}`,
    data,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  );

  return response.data;
};
export const getActiveMysteryTour = async (): Promise<MysteryTourResponse | null> => {
  const userId = localStorage.getItem("userId");
  const token = localStorage.getItem("accessToken");

  if (!userId) {
    return null;
  }

  const response = await axios.get(
    `http://localhost:8080/mystery-tours/active?userId=${userId}`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
      validateStatus: (status) =>
        (status >= 200 && status < 300) || status === 204,
    },
  );

  if (response.status === 204) {
    return null;
  }

  return response.data;
};

export interface MysteryQuestResponse {
  mysteryQuestId: number;
  questOrder: number;
  questName: string;
  questDesc: string;
  verifyType: string;
  targetLat: number | null;
  targetLng: number | null;
  rewardPoint: number;
  status: string;
}

export const getCurrentMysteryQuest = async (
  mysteryTourId: number,
): Promise<MysteryQuestResponse | null> => {
  const token = localStorage.getItem("accessToken");

  const response = await axios.get(
    `http://localhost:8080/mystery-tours/${mysteryTourId}/quests/current`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
      validateStatus: (status) =>
        (status >= 200 && status < 300) || status === 204,
    },
  );

  if (response.status === 204) {
    return null;
  }

  return response.data;
};
export const completeMysteryQuest = async (
  mysteryTourId: number,
  mysteryQuestId: number,
): Promise<MysteryQuestResponse | null> => {

  const token = localStorage.getItem("accessToken");

  const response = await axios.post(
    `http://localhost:8080/mystery-tours/${mysteryTourId}/quests/${mysteryQuestId}/complete`,
    {},
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
      validateStatus: (status) =>
        (status >= 200 && status < 300) || status === 204,
    },
  );

  if (response.status === 204) {
    return null;
  }

  return response.data;
};
export const cancelMysteryTour = async (
  mysteryTourId: number,
): Promise<void> => {

  const token = localStorage.getItem("accessToken");

  await axios.delete(
    `http://localhost:8080/mystery-tours/${mysteryTourId}`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    },
  );
};