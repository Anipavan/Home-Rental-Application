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
  /** Legacy alias kept for screens that read this field directly. */
  role?: Role;
  /**
   * Auth-service serializes the field as {@code userRole} (Java record
   * component name). Newer code reads this; older code reads {@link role}.
   */
  userRole?: string;
  isActive?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface PropertyImageResponseDTO {
  /** Backend stores image rows with a String UUID primary key. */
  id: string;
  /** Building or flat the image belongs to. UUID. */
  propertyId: string;
  /** Server-side filesystem path; not loadable from a browser as-is. */
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
  /** Pre-signed download URL of the photo stored in Document Service. */
  profilePictureUrl?: string;
  /** Pre-signed download URL of the most recent ID proof. */
  idProofUrl?: string;
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
  /** Optional FK id from `ref_states` — sent when the cascading dropdown is used. */
  stateId?: number;
  /** Optional FK id from `ref_cities` — sent when the cascading dropdown is used. */
  cityId?: number;
}

/* Reference dropdown data — Day 7 stabilization */
export interface RefStateDto {
  id: number;
  code: string;
  name: string;
}

export interface RefCityDto {
  id: number;
  stateId: number;
  stateName: string;
  name: string;
  tier?: number;
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
  /** True once the lease deed PDF has been rendered and is downloadable. */
  hasDocument?: boolean;
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

/* ========================================================================== */
/* India Compliance Layer — KYC, Documents, Lease, Compliance, Reviews        */
/* ========================================================================== */

// ---------- KYC Service (port 8092) ----------
export type KycStatus = "PENDING" | "INITIATED" | "VERIFIED" | "FAILED";

export interface InitiateKycRequest {
  aadhaarNumber: string;     // 12 digits
  fullName?: string;
  panNumber?: string;
  consentText: string;
  linkDigilocker?: boolean;
}

export interface VerifyPanRequest {
  userId: string;
  panNumber: string;
  panHolderName: string;
}

export interface KycResponse {
  id: string;
  userId: string;
  kycProvider?: string;
  verificationStatus: KycStatus;
  aadhaarVerified: boolean;
  panVerified: boolean;
  panMasked?: string;
  faceMatchScore?: number;
  digilockerLinked: boolean;
  consentRecorded: boolean;
  kycReferenceId?: string;
  failureReason?: string;
  failureCode?: string;
  verifiedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface KycReport {
  userId: string;
  kycProvider?: string;
  status: KycStatus;
  aadhaarVerified: boolean;
  panVerified: boolean;
  digilockerLinked: boolean;
  consentRecorded: boolean;
  confidenceLevel: "HIGH" | "MEDIUM" | "LOW" | "NONE";
  verifiedAt?: string;
  generatedAt: string;
}

// ---------- Document Service (port 8091) ----------
export type DocumentType = "AADHAAR" | "PAN" | "AGREEMENT" | "PHOTO" | "OTHER";
export type OcrStatus = "PENDING" | "PROCESSING" | "DONE" | "FAILED";

export interface DocumentResponse {
  id: string;
  userId: string;
  documentType: DocumentType;
  originalFilename?: string;
  contentType?: string;
  fileSizeBytes?: number;
  ocrStatus: OcrStatus;
  extractedData?: Record<string, string>;
  fraudFlag?: boolean;
  confidenceScore?: number;
  verifiedBy?: string;
  verifiedAt?: string;
  uploadedAt?: string;
  updatedAt?: string;
}

export interface PreSignedUrl {
  documentId: string;
  url: string;
  expiresAt?: string;
}

export interface ExtractedData {
  documentId: string;
  documentType: DocumentType;
  extractedData?: Record<string, string>;
  fraudFlag?: boolean;
  confidenceScore?: number;
  ocrStatus: OcrStatus;
  extractedAt?: string;
}

// ---------- Lease Service (port 8090) ----------
export type LeaseStatus = "DRAFT" | "ACTIVE" | "EXPIRED" | "TERMINATED";
export type DigitalSignatureStatus = "PENDING" | "SIGNED" | "REJECTED";

export interface CreateLeaseRequest {
  tenantId: string;
  flatId: string;
  ownerId: string;
  startDate: string;
  endDate: string;
  rentAmount: number;
  securityDeposit?: number;
  rentIncrementPercent?: number;
  state?: string;
}

export interface RenewLeaseRequest {
  newEndDate: string;
  newRent?: number;
  notes?: string;
}

export interface TerminateLeaseRequest {
  terminationReason: string;
  terminationDate?: string;
  notes?: string;
}

export interface SignLeaseRequest {
  signatureProvider: string;
  signedBy: string;
}

export interface LeaseResponse {
  id: string;
  tenantId: string;
  flatId: string;
  ownerId: string;
  leaseNumber: string;
  startDate: string;
  endDate: string;
  rentAmount: number;
  securityDeposit?: number;
  rentIncrementPercent?: number;
  status: LeaseStatus;
  reraAgreementNumber?: string;
  documentUrl?: string;
  digitalSignatureStatus?: DigitalSignatureStatus;
  aiRenewalProbability?: number;
  expiryWarningSentAt?: string;
  terminatedAt?: string;
  terminationReason?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface LeaseHistoryEntry {
  id: string;
  leaseId: string;
  eventType: string;
  previousRent?: number;
  newRent?: number;
  changedBy?: string;
  notes?: string;
  changedAt: string;
}

// ---------- Compliance Service (port 8093) ----------
export type ReraStatus = "PENDING" | "REGISTERED" | "EXPIRED";

export interface ReraRegisterRequest {
  propertyId: string;
  ownerId: string;
  state: string;
  reraPortalId?: string;
  additionalNotes?: string;
}

export interface ReraRegistration {
  id: string;
  propertyId: string;
  ownerId: string;
  state: string;
  reraRegistrationNumber?: string;
  reraPortalId?: string;
  registrationStatus: ReraStatus;
  registeredAt?: string;
  expiryDate?: string;
  failureReason?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface GenerateGstInvoiceRequest {
  tenantId: string;
  ownerId: string;
  rentAmount: number;
  annualRentEstimate: number;
  invoiceDate?: string;
}

export interface GstInvoice {
  id: string;
  paymentId: string;
  tenantId: string;
  ownerId: string;
  invoiceNumber: string;
  invoiceDate: string;
  rentAmount: number;
  gstApplicable: boolean;
  gstRatePercent?: number;
  gstAmount?: number;
  totalAmount: number;
  pdfUrl?: string;
  sentViaWhatsapp?: boolean;
  createdAt?: string;
}

// ---------- Review Service (port 8094) ----------
export type ReviewerType = "TENANT" | "OWNER";
export type ReviewTargetType = "PROPERTY" | "OWNER" | "TENANT";
export type ModerationStatus = "PENDING" | "APPROVED" | "REJECTED" | "FLAGGED";

export interface CreateReviewRequest {
  reviewerId: string;
  reviewerType: ReviewerType;
  targetId: string;
  targetType: ReviewTargetType;
  rating: number;        // 1-5
  title?: string;
  body?: string;
  tags?: string[];
}

export interface ModerateReviewRequest {
  decision: "APPROVED" | "REJECTED" | "FLAGGED";
  moderatorId: string;
  reason?: string;
}

export interface ReviewResponse {
  id: string;
  reviewerId: string;
  reviewerType: ReviewerType;
  targetId: string;
  targetType: ReviewTargetType;
  rating: number;
  title?: string;
  body?: string;
  tags?: string[];
  isVerified?: boolean;
  isModerated?: boolean;
  moderationStatus?: ModerationStatus;
  moderationReason?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface RatingSummary {
  targetId: string;
  targetType: ReviewTargetType;
  totalReviews: number;
  averageRating: number;
  ratingHistogram: Record<string, number>;   // {"5": 12, "4": 7, ...}
}
