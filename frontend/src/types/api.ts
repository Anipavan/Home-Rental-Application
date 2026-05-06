export type Role = "ADMIN" | "OWNER" | "TENANT";

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  accessTokenExpiresInSeconds: number;
  userName: string;
  authUserId: string;
  role: Role;
}

export interface LoginRequest {
  userName: string;
  password: string;
}

export interface RegisterRequest {
  userName: string;
  userPassword: string;
  userRole: Role;
  email: string;
  firstName: string;
  lastName: string;
  gender?: string;
  phone?: string;
  address?: string;
  dateOfBirth?: string;
}

export interface MessageResponse {
  message: string;
}

export interface AuthUserResponse {
  id: string;
  userName: string;
  email?: string;
  role: Role;
  isActive?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface PropertyImageResponseDTO {
  id: number;
  propertyId: number;
  imageUrl: string;
  type?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface UserResponseDto {
  id: number;
  authUserId?: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  dateOfBirth?: string;
  gender?: string;
  address?: string;
  profilePictureUrl?: string;
  idProofUrl?: string;
  role?: Role;
  createdAt?: string;
  updatedAt?: string;
}

export interface UserRequestDto {
  authUserId?: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  dateOfBirth?: string;
  gender?: string;
  address?: string;
}

export interface BuildingResponseDTO {
  buildingId: string;
  buildingName: string;
  ownerId: string;
  buildingAddress: string;
  buildingCity: string;
  buildingState: string;
  buildingTotalFloors?: number;
  buildingTotalFlats?: number;
  activeFlatsCount?: number;
  occupiedFlatsCount?: number;
  vacantFlatsCount?: number;
  amenities?: string;
  createdDt?: string;
  updatedDt?: string;
}

export interface BuildingRequestDTO {
  buildingName: string;
  ownerId: string;
  buildingAddress: string;
  buildingCity: string;
  buildingState: string;
  buildingTotalFloors: number;
  buildingTotalFlats: number;
  amenities?: string;
}

export interface FlatResponseDTO {
  id: string;
  buildingId: string;
  buildingName?: string;
  buildingAddress?: string;
  buildingCity?: string;
  flatNumber: string;
  floor?: number;
  bedrooms?: number;
  bathrooms?: number;
  areaSqft?: number;
  rentAmount: number;
  isOccupied?: boolean;
  tenantId?: string;
  leaseStartDate?: string;
  leaseEndDate?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface FlatRequestDTO {
  buildingId: string;
  flatNumber: string;
  floor: number;
  bedrooms: number;
  bathrooms: number;
  areaSqft: number;
  rentAmount: number;
  tenantId?: string;
  leaseStartDate?: string;
  leaseEndDate?: string;
}

export interface AssignFlatRequest {
  tenantId: string;
  leaseStartDate: string;
  leaseEndDate: string;
}

export type AgreementStatus = "PENDING_SIGNATURE" | "SIGNED" | "REJECTED";

export interface AgreementResponseDTO {
  id: string;
  flatId: string;
  buildingId: string;
  tenantId: string;
  ownerId: string;
  rentAmount: number;
  leaseStartDate: string;
  leaseEndDate: string;
  terms?: string;
  status: AgreementStatus;
  signatureData?: string;
  signedAt?: string;
  rejectedAt?: string;
  rejectionReason?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface SignAgreementRequest {
  signatureData: string;
}

export interface RejectAgreementRequest {
  reason: string;
}

export type PaymentStatus =
  | "PENDING"
  | "PROCESSING"
  | "PAID"
  | "OVERDUE"
  | "FAILED"
  | "CANCELLED"
  | "REFUNDED";

export type PaymentMethod =
  | "UPI"
  | "CARD"
  | "NET_BANKING"
  | "WALLET"
  | "BANK_TRANSFER"
  | "CASH";

export type UpiApp =
  | "GPAY"
  | "PHONEPE"
  | "PAYTM"
  | "BHIM"
  | "AMAZONPAY"
  | "CRED"
  | "WHATSAPP"
  | "OTHER";

export type WalletProvider =
  | "PAYTM"
  | "AMAZONPAY"
  | "PHONEPE"
  | "MOBIKWIK"
  | "FREECHARGE"
  | "JIOMONEY"
  | "OLA_MONEY"
  | "OTHER";

export type CardNetwork =
  | "VISA"
  | "MASTERCARD"
  | "RUPAY"
  | "AMEX"
  | "DINERS"
  | "DISCOVER"
  | "OTHER";

export interface PaymentResponse {
  id: string;
  tenantId: string;
  flatId: string;
  ownerId: string;
  amount: number;
  lateFee?: number;
  totalAmount?: number;
  dueDate: string;
  paymentDate?: string;
  status: PaymentStatus;
  paymentMethod?: PaymentMethod;
  upiApp?: UpiApp;
  walletProvider?: WalletProvider;
  cardNetwork?: CardNetwork;
  cardLast4?: string;
  upiVpa?: string;
  transactionId?: string;
  gatewayOrderId?: string;
  gatewayName?: string;
  failureReason?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreatePaymentRequest {
  tenantId: string;
  flatId: string;
  ownerId: string;
  amount: number;
  dueDate: string;
}

export interface PayCashRequest {
  ownerId: string;
  reference?: string;
}

export interface InitiatePaymentRequest {
  paymentId: string;
  paymentMethod: PaymentMethod;
  upiApp?: UpiApp;
  upiVpa?: string;
  walletProvider?: WalletProvider;
  cardNetwork?: CardNetwork;
  cardLast4?: string;
  bankReference?: string;
  returnUrl?: string;
}

export interface InitiatePaymentResponse {
  paymentId: string;
  paymentMethod: PaymentMethod;
  gatewayName: string;
  gatewayOrderId: string;
  amount: number;
  currency: string;
  redirectUrl?: string;
  upiIntentUrl?: string;
  upiCollectStatus?: string;
  bankAccountNumber?: string;
  bankIfsc?: string;
  bankAccountName?: string;
}

export interface VerifyPaymentRequest {
  paymentId: string;
  gatewayOrderId: string;
  transactionId: string;
  signature: string;
}

export type MaintenanceStatus =
  | "OPEN"
  | "IN_PROGRESS"
  | "RESOLVED"
  | "CLOSED";
export type MaintenancePriority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type MaintenanceCategory =
  | "PLUMBING"
  | "ELECTRICAL"
  | "PAINTING"
  | "APPLIANCE"
  | "CLEANING"
  | "PEST_CONTROL"
  | "GENERAL";

export interface MaintenanceComment {
  userId: string;
  comment: string;
  timestamp: string;
}

export interface MaintenanceRequestResponse {
  id: string;
  requestNumber?: string;
  tenantId: string;
  flatId: string;
  ownerId?: string;
  category: MaintenanceCategory;
  title: string;
  description: string;
  priority: MaintenancePriority;
  status: MaintenanceStatus;
  images?: string[];
  assignedTo?: string;
  createdAt: string;
  updatedAt?: string;
  resolvedAt?: string;
  comments?: MaintenanceComment[];
}

export interface CreateRequestDto {
  flatId: string;
  tenantId: string;
  category: MaintenanceCategory;
  priority: MaintenancePriority;
  title: string;
  description: string;
}

export type NotificationType = "EMAIL" | "SMS" | "PUSH";

export type NotificationCategory =
  | "USER_REGISTRATION"
  | "PASSWORD_RESET"
  | "PAYMENT_CREATED"
  | "PAYMENT_REMINDER"
  | "PAYMENT_OVERDUE"
  | "PAYMENT_RECEIPT"
  | "MAINTENANCE_CREATED"
  | "MAINTENANCE_ASSIGNED"
  | "MAINTENANCE_RESOLVED"
  | "LEASE_WELCOME"
  | "LEASE_EXPIRY"
  | "GENERIC";

export interface SendNotificationRequest {
  userId: string;
  type: NotificationType;
  category?: NotificationCategory;
  subject?: string;
  message?: string;
  recipient?: string;
  templateVariables?: Record<string, unknown>;
}

export interface NotificationResponse {
  id?: string;
  userId: string;
  type: NotificationType;
  category?: NotificationCategory;
  subject?: string;
  message?: string;
  status?: string;
  sentAt?: string;
  deliveredAt?: string;
  errorMessage?: string;
  retryCount?: number;
}

export interface ApiError {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  errorCode?: string;
  path?: string;
}
