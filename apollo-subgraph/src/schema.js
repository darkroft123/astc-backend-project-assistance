import { parse } from 'graphql';

export const typeDefs = parse(/* GraphQL */ `
  extend schema
    @link(url: "https://specs.apollo.dev/federation/v2.3", import: ["@key", "@shareable"])

  scalar Date

  type Query {
    getMyProjects: [ProjectOutput]
    getDashboardPM(fromDate: String, toDate: String, projectId: String, userId: String): DashboardOutput
    getNotificationsByType(userId: String, type: String): [NotificationOutput]
    listAttendanceAlerts(page: Int!, size: Int!, status: String, type: String, fromDate: String, toDate: String): AlertPageOutput
    listVacations(userId: String, status: String, page: Int, size: Int): [VacationRequestOutput]
    getVacationBalance(userId: String): VacationBalanceOutput
    getJustificationDetail(id: String): JustificationOutput
    getAlertDetail(id: String): AlertDetailOutput
    listShiftTemplates(projectId: String): [ShiftTemplateOutput]
    listholidays(targetId: String, year: Int!): [HolidayOutput]
    getHolidayExceptionsForUser(userId: String): [HolidayOutput]
    getProjectById(id: String): ProjectOutput
    listScheduleOverrides(projectId: String, userId: String, page: Int, size: Int): [ScheduleOutput]
    getMyVacationEligibility(projectId: String): VacationEligibilityOutput
    exportAttendance(projectId: String, userId: String, fromDate: String, toDate: String, status: String): ExportResultOutput
    getGlobalCollections: [CollectionOutput]
    getProjectMembers(projectId: String): [ProjectMemberOutput]
    listTeamAbsences(page: Int!, size: Int!, projectId: String, userId: String, fromDate: String, toDate: String, type: String, justified: Boolean): AbsencePageOutput
    getEffectiveSchedule(userId: String, projectId: String, date: String): EffectiveScheduleOutput
    listTeamAttendance(page: Int!, size: Int!, projectId: String, userId: String, fromDate: String, toDate: String, status: String): AttendancePageOutput
    getAlertsByUser(userId: String): [NotificationOutput]
    getAllAlerts: [NotificationOutput]
    listJustificationsPM(page: Int!, size: Int!, userId: String, status: String, fromDate: String, toDate: String): JustificationPageOutput
    getAllProjects: [ProjectOutput]
    getAdminSetting(key: String): String
    hasApprovedVacation(userId: String, date: Date): Boolean
    isHolidayForUser(projectId: String, userId: String, date: Date): Boolean
    listTeamMembers: [UserOutput]
    listProjectManagers: [UserOutput]
    getCollectionsForProject(projectId: String): [CollectionOutput]
  }

  type Mutation {
    createShiftTemplate(input: ShiftTemplateInput): ShiftTemplateOutput
    updateProject(id: String, input: ProjectInput): ProjectOutput
    createProject(input: ProjectInput): ProjectOutput
    assignSchedule(input: ScheduleInput): ScheduleOutput
    bulkcreateholidays(targetId: String, dates: [Date], name: String, type: String): [HolidayOutput]
    deleteCollectionItem(id: String): Boolean
    unlinkProjectFromCollection(projectId: String, collectionId: String): Boolean
    approveAlert(alertId: String): AlertOutput
    deleteShiftTemplate(id: String): Boolean
    updateAdminSetting(key: String, value: String): Boolean
    rejectVacation(requestId: String, comment: String): VacationRequestOutput
    requestObservation(justificationId: String, comment: String): JustificationOutput
    addProjectMember(input: ProjectMemberInput): ProjectMemberOutput
    deleteProject(id: String): Boolean
    createHoliday(input: HolidayInput): HolidayOutput
    approveVacation(requestId: String, discountDays: Int): VacationRequestOutput
    requestVacation(input: VacationRequestInput): VacationRequestOutput
    approveJustification(justificationId: String, comment: String): JustificationOutput
    deleteProjectMember(id: String): Boolean
    deleteSchedule(userId: String, projectId: String): Boolean
    updateProjectMember(id: String, role: String): ProjectMemberOutput
    deleteCollection(id: String): Boolean
    addDatesToCollection(collectionId: String, dates: [Date], name: String): Boolean
    createAlert(userId: String, projectId: String, type: String, detail: String, latitude: Float, longitude: Float): AlertOutput
    cancelVacation(requestId: String): VacationRequestOutput
    deleteAllHolidays(targetId: String, type: String): Boolean
    importHolidaysFromCsv(csvContent: String, targetId: String, type: String, defaultName: String): ImportHolidaysResultOutput
    cancelAlert(alertId: String): AlertOutput
    createNotification(input: NotificationInput): NotificationOutput
    createCollection(input: CollectionInput, items: [CollectionItemInput]): CollectionOutput
    rejectJustification(justificationId: String, comment: String): JustificationOutput
    linkProjectToCollection(projectId: String, collectionId: String): Boolean
    deleteHoliday(id: String): Boolean
  }

  # ============ PROJECTS ============
  type ProjectOutput @key(fields: "id") {
    id: String
    name: String
    description: String
    status: String
    timezone: String
    startDate: String
    endDate: String
    workStartTime: String
    workEndTime: String
    absenceCutoffTime: String
    graceMinutes: Int
    latitude: Float
    longitude: Float
    radius: Float
    shiftType: String
    currency: String
    budget: String
    responsibleId: String
    vacationEligibilityDays: Int
    holidays: [String]
    createdAt: String
    members: [ProjectMemberOutput]
  }

  type ProjectMemberOutput {
    id: String
    projectId: String
    userId: String
    role: String
    hasCustomSchedule: Boolean
    createdAt: String
    user: UserOutput
  }

  # ============ USERS ============
  type UserOutput @key(fields: "id") {
    id: ID!
    username: String @shareable
    email: String @shareable
    firstName: String @shareable
    lastName: String @shareable
    avatarUrl: String @shareable
    roleId: String @shareable
    roleCode: String @shareable
    roleName: String @shareable
  }

  # ============ ATTENDANCE / ABSENCES ============
  type AttendanceOutput @key(fields: "id") {
    id: ID!
    userId: String @shareable
    projectId: String @shareable
    date: String @shareable
    checkIn: String @shareable
    checkOut: String @shareable
    status: String @shareable
    latitude: Float @shareable
    longitude: Float @shareable
    photoUrl: String @shareable
  }

  type AttendancePageOutput @shareable {
    items: [AttendanceOutput]
    page: Int!
    size: Int!
    total: Int!
  }

  type AbsenceOutput @key(fields: "id") {
    id: String
    userId: String @shareable
    projectId: String @shareable
    date: String @shareable
    type: String @shareable
    justified: Boolean @shareable
  }

  type AbsencePageOutput @shareable {
    items: [AbsenceOutput]
    page: Int!
    size: Int!
    total: Int!
  }

  type JustificationOutput @key(fields: "id") {
    id: ID!
    absenceId: String @shareable
    userId: String @shareable
    description: String @shareable
    documentUrl: String @shareable
    status: String @shareable
    comment: String @shareable
    submittedAt: String @shareable
    reviewedAt: String @shareable
    reviewedBy: String @shareable
    absenceDate: String @shareable
    absenceType: String @shareable
    history: [JustificationHistoryOutput] @shareable
  }

  type JustificationHistoryOutput {
    id: String @shareable
    justificationId: String @shareable
    previousStatus: String @shareable
    newStatus: String @shareable
    comment: String @shareable
    changedBy: String @shareable
    changedAt: String @shareable
  }

  type JustificationPageOutput @shareable {
    items: [JustificationOutput]
    page: Int!
    size: Int!
    total: Int!
  }

  type DashboardOutput @shareable {
    totalAttendances: Int
    totalAbsences: Int
    pendingJustifications: Int
  }

  type ExportResultOutput {
    fileName: String
    content: String
  }

  # ============ ALERTS ============
  type AlertOutput @key(fields: "id") {
    id: ID!
    type: String
    status: String
    detail: String
    message: String
    userId: String
    projectId: String
    createdAt: String
    latitude: Float
    longitude: Float
  }

  type AlertDetailOutput {
    id: ID!
    type: String
    status: String
    detail: String
    severity: String
    message: String
    userId: String
    projectId: String
    createdAt: String
    latitude: Float
    longitude: Float
  }

  type AlertPageOutput {
    items: [AlertOutput]
    pageInfo: PageInfoOutput
    page: Int!
    size: Int!
    total: Int!
  }

  type PageInfoOutput {
    page: Int! @shareable
    size: Int! @shareable
    totalItems: Int @shareable
    totalElements: Int @shareable
    totalPages: Int! @shareable
  }

  # ============ VACATIONS ============
  type VacationRequestOutput {
    id: String
    userId: String
    projectId: String
    startDate: String
    endDate: String
    businessDays: Int
    status: String
    comment: String
    reviewedBy: String
    reviewedAt: String
    createdAt: String
  }

  type VacationBalanceOutput {
    id: String
    userId: String
    year: Int
    totalDays: Int
    usedDays: Int
    pendingDays: Int
    availableDays: Int
  }

  type VacationEligibilityOutput {
    isEligible: Boolean!
    requiredDays: Int!
    daysElapsed: Int!
    daysRemaining: Int
    joinDate: String
  }

  # ============ HOLIDAYS ============
  type HolidayOutput {
    id: String
    date: String
    name: String
    type: String
    targetId: String
  }

  type ImportHolidaysResultOutput {
    total: Int!
    created: Int!
    skipped: Int!
    errors: Int!
    holidays: [HolidayOutput]
  }

  # ============ SCHEDULES ============
  type ShiftTemplateOutput {
    id: String
    projectId: String
    name: String
    shiftType: String
    timezone: String
    graceMinutes: Int
    rotationWorkDays: Int
    rotationRestDays: Int
    rotationShiftStartTime: String
    rotationShiftEndTime: String
    workStartTime: String
    workEndTime: String
    absenceCutoffTime: String
    mondayStart: String
    mondayEnd: String
    tuesdayStart: String
    tuesdayEnd: String
    wednesdayStart: String
    wednesdayEnd: String
    thursdayStart: String
    thursdayEnd: String
    fridayStart: String
    fridayEnd: String
    saturdayStart: String
    saturdayEnd: String
    sundayStart: String
    sundayEnd: String
    validFrom: String
    validUntil: String
    createdAt: String
  }

  type ScheduleOutput {
    id: String
    userId: String
    projectId: String
    shiftType: String
    timezone: String
    graceMinutes: Int
    rotationWorkDays: Int
    rotationRestDays: Int
    rotationShiftStartTime: String
    rotationShiftEndTime: String
    workStartTime: String
    workEndTime: String
    absenceCutoffTime: String
    mondayStart: String
    mondayEnd: String
    tuesdayStart: String
    tuesdayEnd: String
    wednesdayStart: String
    wednesdayEnd: String
    thursdayStart: String
    thursdayEnd: String
    fridayStart: String
    fridayEnd: String
    saturdayStart: String
    saturdayEnd: String
    sundayStart: String
    sundayEnd: String
    validFrom: String
    validUntil: String
  }

  type EffectiveScheduleOutput {
    workDay: Boolean!
    expectedStartTime: String
    expectedEndTime: String
    graceMinutes: Int
    absenceCutoffTime: String
    shiftType: String
    timezone: String
  }

  # ============ NOTIFICATIONS ============
  type NotificationOutput {
    id: String
    userId: String
    user: UserOutput
    type: String
    severity: String
    title: String
    message: String
    referenceType: String
    referenceId: String
    createdAt: String
  }

  # ============ COLLECTIONS ============
  type CollectionOutput {
    id: String
    name: String
    ownerId: String
    scope: String
    createdAt: String
    items: [CollectionItemOutput]
  }

  type CollectionItemOutput {
    id: String
    collectionId: String
    name: String
    date: String
  }

  # ============ INPUTS ============
  input ProjectInput {
    name: String
    description: String
    status: String
    timezone: String
    startDate: String
    endDate: String
    workStartTime: String
    workEndTime: String
    absenceCutoffTime: String
    graceMinutes: Int
    currency: String
    budget: String
    responsibleId: String
    vacationEligibilityDays: Int
    holidays: [String]
    members: [ProjectMemberInput]
  }

  input ProjectMemberInput {
    projectId: String
    userId: String
    role: String
  }

  input ShiftTemplateInput {
    projectId: String
    name: String
    shiftType: String
    timezone: String
    graceMinutes: Int
    rotationWorkDays: Int
    rotationRestDays: Int
    rotationShiftStartTime: String
    rotationShiftEndTime: String
    workStartTime: String
    workEndTime: String
    absenceCutoffTime: String
    mondayStart: String
    mondayEnd: String
    tuesdayStart: String
    tuesdayEnd: String
    wednesdayStart: String
    wednesdayEnd: String
    thursdayStart: String
    thursdayEnd: String
    fridayStart: String
    fridayEnd: String
    saturdayStart: String
    saturdayEnd: String
    sundayStart: String
    sundayEnd: String
    validFrom: String
    validUntil: String
  }

  input ScheduleInput {
    userId: String
    projectId: String
    shiftType: String
    timezone: String
    graceMinutes: Int
    rotationWorkDays: Int
    rotationRestDays: Int
    rotationShiftStartTime: String
    rotationShiftEndTime: String
    workStartTime: String
    workEndTime: String
    absenceCutoffTime: String
    mondayStart: String
    mondayEnd: String
    tuesdayStart: String
    tuesdayEnd: String
    wednesdayStart: String
    wednesdayEnd: String
    thursdayStart: String
    thursdayEnd: String
    fridayStart: String
    fridayEnd: String
    saturdayStart: String
    saturdayEnd: String
    sundayStart: String
    sundayEnd: String
    validFrom: String
    validUntil: String
  }

  input HolidayInput {
    date: String
    name: String
    type: String
    targetId: String
  }

  input VacationRequestInput {
    userId: String
    projectId: String
    startDate: String
    endDate: String
    businessDays: Int
    comment: String
  }

  input NotificationInput {
    userId: String
    type: String
    severity: String
    title: String
    message: String
    referenceType: String
    referenceId: String
  }

  input CollectionInput {
    name: String
    ownerId: String
    scope: String
  }

  input CollectionItemInput {
    name: String
    date: String
  }
`);