
import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

// Main UserAdminData class
@Parcelize
data class UserAdminData(
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("barbershop_name") @set:PropertyName("barbershop_name") var barbershopName: String = "",
    @get:PropertyName("barbershop_identifier") @set:PropertyName("barbershop_identifier") var barbershopIdentifier: String = "",
    @get:PropertyName("company_name") @set:PropertyName("company_name") var companyName: String = "",
    @get:PropertyName("owner_name") @set:PropertyName("owner_name") var ownerName: String = "Owner Barbershop",
    @get:PropertyName("email") @set:PropertyName("email") var email: String = "",
    @get:PropertyName("password") @set:PropertyName("password") var password: String = "",
    @get:PropertyName("phone") @set:PropertyName("phone") var phone: String = "",
    @get:PropertyName("image_company_profile") @set:PropertyName("image_company_profile") var imageCompanyProfile: String = "",
    @get:PropertyName("subscription_status") @set:PropertyName("subscription_status") var subscriptionStatus: Boolean = true,
    @get:PropertyName("operational_hour") @set:PropertyName("operational_hour") var operationalHour: @RawValue OperationalHour = OperationalHour(),
    @get:PropertyName("list_customers") @set:PropertyName("list_customers") var listCustomers: @RawValue List<String> = emptyList()
) : Parcelable

// Operational hours for different days
@Parcelize
data class OperationalHour(
    @get:PropertyName("sunday") @set:PropertyName("sunday") var sunday: @RawValue DailySchedule = DailySchedule(),
    @get:PropertyName("saturday") @set:PropertyName("saturday") var saturday: @RawValue DailySchedule = DailySchedule(),
    @get:PropertyName("friday") @set:PropertyName("friday") var friday: @RawValue DailySchedule = DailySchedule(),
    @get:PropertyName("thursday") @set:PropertyName("thursday") var thursday: @RawValue DailySchedule = DailySchedule(),
    @get:PropertyName("wednesday") @set:PropertyName("wednesday") var wednesday: @RawValue DailySchedule = DailySchedule(),
    @get:PropertyName("tuesday") @set:PropertyName("tuesday") var tuesday: @RawValue DailySchedule = DailySchedule(),
    @get:PropertyName("monday") @set:PropertyName("monday") var monday: @RawValue DailySchedule = DailySchedule()
) : Parcelable

@Parcelize
data class DailySchedule(
    @get:PropertyName("open") @set:PropertyName("open") var open: String = "",
    @get:PropertyName("close") @set:PropertyName("close") var close: String = ""
) : Parcelable

// Best deal data class
@Parcelize
data class BestDeal(
    @get:PropertyName("applies_to") @set:PropertyName("applies_to") var appliesTo: String = "",
    @get:PropertyName("best_deal_amount") @set:PropertyName("best_deal_amount") var bestDealAmount: Int = 0,
    @get:PropertyName("best_deal_category") @set:PropertyName("best_deal_category") var bestDealCategory: String = "",
    @get:PropertyName("best_deal_code") @set:PropertyName("best_deal_code") var bestDealCode: String = "",
    @get:PropertyName("best_deal_desc") @set:PropertyName("best_deal_desc") var bestDealDesc: String = "",
    @get:PropertyName("best_deal_format") @set:PropertyName("best_deal_format") var bestDealFormat: String = "",
    @get:PropertyName("best_deal_title") @set:PropertyName("best_deal_title") var bestDealTitle: String = "",
    @get:PropertyName("effective_date") @set:PropertyName("effective_date") var effectiveTimestamp: Timestamp = Timestamp.now(),
    @get:PropertyName("expired_date") @set:PropertyName("expired_date") var expiredTimestamp: Timestamp = Timestamp.now(),
    @get:PropertyName("repetition_status") @set:PropertyName("repetition_status") var repetitionStatus: Boolean = false,
    @get:PropertyName("repetition_time") @set:PropertyName("repetition_time") var repetitionTime: List<String> = emptyList(),
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("target") @set:PropertyName("target") var target: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable

// Bundling package data class
@Parcelize
data class BundlingPackage(
    @get:PropertyName("list_items") @set:PropertyName("list_items") var listItems: @RawValue List<String> = emptyList(),
    @get:PropertyName("package_counting") @set:PropertyName("package_counting") var packageCounting: @RawValue Map<String, Int> = emptyMap(),
    @get:PropertyName("package_desc") @set:PropertyName("package_desc") var packageDesc: String = "",
    @get:PropertyName("package_price") @set:PropertyName("package_price") var packagePrice: Int = 0,
    @get:PropertyName("package_name") @set:PropertyName("package_name") var packageName: String = "",
    @get:PropertyName("results_share_amount") @set:PropertyName("results_share_amount") var resultsShareAmount: Int = 0,
    @get:PropertyName("package_rating") @set:PropertyName("package_rating") var packageRating: Double = 5.0,
    @get:PropertyName("results_share_format") @set:PropertyName("results_share_format") var resultsShareFormat: String = "",
    var listItemDetails: @RawValue List<Service> = emptyList(),
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable

// Category data class
@Parcelize
data class Category(
    @get:PropertyName("category_name") @set:PropertyName("category_name") var categoryName: String = "",
    @get:PropertyName("category_type") @set:PropertyName("category_type") var categoryType: String = "",
    @get:PropertyName("results_share_amount") @set:PropertyName("results_share_amount") var resultsShareAmount: Int = 0,
    @get:PropertyName("results_share_format") @set:PropertyName("results_share_format") var resultsShareFormat: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable

// Division data class
@Parcelize
data class Division(
    @get:PropertyName("division_description") @set:PropertyName("division_description") var divisionDescription: String = "",
    @get:PropertyName("division_name") @set:PropertyName("division_name") var divisionName: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
) : Parcelable

// Employee data class
@Parcelize
data class Employee(
    @get:PropertyName("accumulated_lateness") @set:PropertyName("accumulated_lateness") var accumulatedLateness: @RawValue Map<String, Int> = emptyMap(),
    @get:PropertyName("amount_of_bon") @set:PropertyName("amount_of_bon") var amountOfBon: Int = 0,
    @get:PropertyName("availability_status") @set:PropertyName("availability_status") var availabilityStatus: Boolean = true,
    @get:PropertyName("customer_counting") @set:PropertyName("customer_counting") var customerCounting: @RawValue Map<String, Int> = emptyMap(),
    @get:PropertyName("current_queue") @set:PropertyName("current_queue") var currentQueue: String = "",
    @get:PropertyName("fullname") @set:PropertyName("fullname") var fullname: String = "",
    @get:PropertyName("gender") @set:PropertyName("gender") var gender: String = "",
    @get:PropertyName("phone") @set:PropertyName("phone") var phone: String = "",
    @get:PropertyName("photo_profile") @set:PropertyName("photo_profile") var photoProfile: String = "",
    @get:PropertyName("employee_rating") @set:PropertyName("employee_rating") var employeeRating: Double = 5.0,
    @get:PropertyName("appointment_list") @set:PropertyName("appointment_list") var appointmentList: @RawValue List<String> = emptyList(),
    @get:PropertyName("pin") @set:PropertyName("pin") var pin: String = "",
    @get:PropertyName("point") @set:PropertyName("point") var point: Int = 0,
    @get:PropertyName("positions") @set:PropertyName("positions") var positions: String = "",
    @get:PropertyName("product_commission") @set:PropertyName("product_commission") var productCommission: @RawValue Map<String, Int> = emptyMap(),
    @get:PropertyName("role") @set:PropertyName("role") var role: String = "",
    @get:PropertyName("role_detail") @set:PropertyName("role_detail") var roleDetail: String = "",
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("salary") @set:PropertyName("salary") var salary: Int = 0,
    @get:PropertyName("service_commission") @set:PropertyName("service_commission") var serviceCommission: @RawValue Map<String, Int> = emptyMap(),
    @get:PropertyName("specialization_cost") @set:PropertyName("specialization_cost") var specializationCost: Int = 0,
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("username") @set:PropertyName("username") var username: String = "",
    var userRef: String = "",
    var outletRef: String = "",
    var restOfQueue: Int = 0
) : Parcelable

@Parcelize
data class AttendanceRecord(
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable

@Parcelize
data class LeavePermission(
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable

// Outlet data class
@Parcelize
data class Outlet(
    @get:PropertyName("active_devices") @set:PropertyName("active_devices") var activeDevices: Int = 0,
    @get:PropertyName("img_outlet") @set:PropertyName("img_outlet") var imgOutlet: String = "",
    @get:PropertyName("list_best_deals") @set:PropertyName("list_best_deals") var listBestDeals: @RawValue List<String> = emptyList(),
    @get:PropertyName("list_bundling") @set:PropertyName("list_bundling") var listBundling: @RawValue List<String> = emptyList(),
    @get:PropertyName("list_employees") @set:PropertyName("list_employees") var listEmployees: @RawValue List<String> = emptyList(),
    @get:PropertyName("list_products") @set:PropertyName("list_products") var listProducts: @RawValue List<String> = emptyList(),
    @get:PropertyName("list_services") @set:PropertyName("list_services") var listServices: @RawValue List<String> = emptyList(),
    @get:PropertyName("open_status") @set:PropertyName("open_status") var openStatus: Boolean = false,
    @get:PropertyName("outlet_access_code") @set:PropertyName("outlet_access_code") var outletAccessCode: String = "",
    @get:PropertyName("outlet_rating") @set:PropertyName("outlet_rating") var outletRating: Double = 5.0,
    @get:PropertyName("outlet_name") @set:PropertyName("outlet_name") var outletName: String = "",
    @get:PropertyName("outlet_phone_number") @set:PropertyName("outlet_phone_number") var outletPhoneNumber: String = "",
    @get:PropertyName("last_updated") @set:PropertyName("last_updated") var lastUpdated: Timestamp = Timestamp.now(),
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("tagline_or_desc") @set:PropertyName("tagline_or_desc") var taglineOrDesc: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    var isCollapseCard: Boolean = true,
//    @get:PropertyName("status_active") @set:PropertyName("status_active") var statusActive: Boolean = false,
//    var dailyCapitalIsEmpty: Boolean = true,
) : Parcelable

@Parcelize
data class DailyCapital(
    @get:PropertyName("created_by") @set:PropertyName("created_by") var createdBy: String = "",
    @get:PropertyName("created_on") @set:PropertyName("created_on") var createdOn: Timestamp = Timestamp.now(),
    @get:PropertyName("outlet_capital") @set:PropertyName("outlet_capital") var outletCapital: Int = 0,
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("outlet_uid") @set:PropertyName("outlet_uid") var outletUid: String = "",
    @get:PropertyName("writer_info") @set:PropertyName("writer_info") var writerInfo: WriterInfo = WriterInfo()
) : Parcelable

@Parcelize
data class WriterInfo(
    @get:PropertyName("user_jobs") @set:PropertyName("user_jobs") var userJobs: String = "",
    @get:PropertyName("user_photo") @set:PropertyName("user_photo") var userPhoto: String = "",
    @get:PropertyName("user_ref") @set:PropertyName("user_ref") var userRef: String = ""
) : Parcelable


@Parcelize
data class Expenditure(
    @get:PropertyName("created_by") @set:PropertyName("created_by") var createdBy: String = "",
    @get:PropertyName("created_on") @set:PropertyName("created_on") var createdOn: Timestamp = Timestamp.now(),
    @get:PropertyName("expanditure_list") @set:PropertyName("expanditure_list") var expenditureList: List<ExpenditureItem> = listOf(),
    @get:PropertyName("outlet_uid") @set:PropertyName("outlet_uid") var outletUid: String = "",
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("total_expenditure") @set:PropertyName("total_expenditure") var totalExpenditure: Int = 0,
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("writer_info") @set:PropertyName("writer_info") var writerInfo: WriterInfo = WriterInfo()
) : Parcelable

@Parcelize
data class ExpenditureItem(
    @get:PropertyName("expenditure_amount") @set:PropertyName("expenditure_amount") var expenditureAmount: Int = 0,
    @get:PropertyName("expenditure_title") @set:PropertyName("expenditure_title") var expenditureTitle: String = "",
    @get:PropertyName("information_note") @set:PropertyName("information_note") var informationNote: String = ""
) : Parcelable

@Parcelize
data class Appointment(
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable

// Product data class
@Parcelize
data class Product(
    @get:PropertyName("category_detail") @set:PropertyName("category_detail") var categoryDetail: String = "",
    @get:PropertyName("img_product") @set:PropertyName("img_product") var imgProduct: String = "",
    @get:PropertyName("product_category") @set:PropertyName("product_category") var productCategory: String = "",
    @get:PropertyName("product_counting") @set:PropertyName("product_counting") var productCounting: @RawValue Map<String, Int> = emptyMap(),
    @get:PropertyName("product_description") @set:PropertyName("product_description") var productDescription: String = "",
    @get:PropertyName("product_name") @set:PropertyName("product_name") var productName: String = "",
    @get:PropertyName("product_price") @set:PropertyName("product_price") var productPrice: Int = 0,
    @get:PropertyName("product_rating") @set:PropertyName("product_rating") var productRating: Double = 4.5,
    @get:PropertyName("results_share_amount") @set:PropertyName("results_share_amount") var resultsShareAmount: Int = 0,
    @get:PropertyName("results_share_format") @set:PropertyName("results_share_format") var resultsShareFormat: String = "",
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("seller") @set:PropertyName("seller") var seller: @RawValue Seller = Seller(),
    @get:PropertyName("tag") @set:PropertyName("tag") var tag: @RawValue List<String> = emptyList(),
    @get:PropertyName("stock_quantity") @set:PropertyName("stock_quantity") var stockQuantity: Int = 0,
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
//    @get:PropertyName("sold_quantity") @set:PropertyName("sold_quantity") var soldQuantity: Int = 0
) : Parcelable

@Parcelize
data class Seller(
    @get:PropertyName("seller_name") @set:PropertyName("seller_name") var sellerName: String = "",
    @get:PropertyName("seller_phone") @set:PropertyName("seller_phone") var sellerPhone: String = "",
    @get:PropertyName("seller_profile") @set:PropertyName("seller_profile") var sellerProfile: String = "",
    @get:PropertyName("uid_seller") @set:PropertyName("uid_seller") var uidSeller: String = "",
) : Parcelable

// Role data class
@Parcelize
data class Role(
    @get:PropertyName("job_desc") @set:PropertyName("job_desc") var jobDesc: String = "",
    @get:PropertyName("permissions") @set:PropertyName("permissions") var permissions: @RawValue Map<String, Boolean> = emptyMap(),
    @get:PropertyName("role_name") @set:PropertyName("role_name") var roleName: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable

// Service data class
@Parcelize
data class Service(
    @get:PropertyName("category_detail") @set:PropertyName("category_detail") var categoryDetail: String = "",
    @get:PropertyName("results_share_amount") @set:PropertyName("results_share_amount") var resultsShareAmount: Int = 0,
    @get:PropertyName("results_share_format") @set:PropertyName("results_share_format") var resultsShareFormat: String = "",
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("service_category") @set:PropertyName("service_category") var serviceCategory: String = "",
    @get:PropertyName("service_counting") @set:PropertyName("service_counting") var serviceCounting: @RawValue Map<String, Int> = emptyMap(),
    @get:PropertyName("service_desc") @set:PropertyName("service_desc") var serviceDesc: String = "",
    @get:PropertyName("service_icon") @set:PropertyName("service_icon") var serviceIcon: String = "",
    @get:PropertyName("service_img") @set:PropertyName("service_img") var serviceImg: String = "",
    @get:PropertyName("service_name") @set:PropertyName("service_name") var serviceName: String = "",
    @get:PropertyName("service_price") @set:PropertyName("service_price") var servicePrice: Int = 0,
    @get:PropertyName("service_rating") @set:PropertyName("service_rating") var serviceRating: Double = 5.0,
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable
