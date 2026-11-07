/** Editable part of a user's profile (GET/PUT /api/user/profile/{id}). */
export interface UserProfile {
  username?: string;
  fullName?: string;
  makerStory?: string;
  workshopImageUrl?: string;
  city?: string;
}
