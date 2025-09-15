package com.example.barberlink.DataClass

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

interface UserData {
    var uid: String
    var email: String
    var password: String
    var phone: String
    var userRef: String
}

// Main UserAdminData class
@Parcelize
data class UserAdminData(
    @get:PropertyName("uid") @set:PropertyName("uid") override var uid: String = "",
    @get:PropertyName("barbershop_name") @set:PropertyName("barbershop_name") var barbershopName: String = "",
    @get:PropertyName("barbershop_identifier") @set:PropertyName("barbershop_identifier") var barbershopIdentifier: String = "",
    @get:PropertyName("company_name") @set:PropertyName("company_name") var companyName: String = "",
    @get:PropertyName("owner_name") @set:PropertyName("owner_name") var ownerName: String = "Owner Barbershop",
    @get:PropertyName("email") @set:PropertyName("email") override var email: String = "",
    @get:PropertyName("password") @set:PropertyName("password") override var password: String = "",
    @get:PropertyName("phone") @set:PropertyName("phone") override var phone: String = "",
    @get:PropertyName("image_company_profile") @set:PropertyName("image_company_profile") var imageCompanyProfile: String = "",
    @get:PropertyName("subscription_status") @set:PropertyName("subscription_status") var subscriptionStatus: Boolean = true,
    @get:PropertyName("operational_hour") @set:PropertyName("operational_hour") var operationalHours: @RawValue OperationalHour = OperationalHour(),
    @get:PropertyName("account_verification") @set:PropertyName("account_verification") var accountVerification: Boolean = false,
    @get:Exclude @set:Exclude override var userRef: String = "", // Hanya Digunakan pada saat Sign Up
) : Parcelable, UserData {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    fun deepCopy(
        copyOperationalHour: Boolean,
        copySunday: Boolean,
        copySaturday: Boolean,
        copyFriday: Boolean,
        copyThursday: Boolean,
        copyWednesday: Boolean,
        copyTuesday: Boolean,
        copyMonday: Boolean
    ): UserAdminData {
        return UserAdminData(
            uid = this.uid,
            barbershopName = this.barbershopName,
            barbershopIdentifier = this.barbershopIdentifier,
            companyName = this.companyName,
            ownerName = this.ownerName,
            email = this.email,
            password = this.password,
            phone = this.phone,
            imageCompanyProfile = this.imageCompanyProfile,
            subscriptionStatus = this.subscriptionStatus,
            operationalHours = if (copyOperationalHour) {
                this.operationalHours.deepCopy(copySunday, copySaturday, copyFriday, copyThursday, copyWednesday, copyTuesday, copyMonday)
            } else {
                this.operationalHours
            },
            accountVerification = this.accountVerification,
            userRef = this.userRef
        )
    }
}

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
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    fun deepCopy(
        copySunday: Boolean,
        copySaturday: Boolean,
        copyFriday: Boolean,
        copyThursday: Boolean,
        copyWednesday: Boolean,
        copyTuesday: Boolean,
        copyMonday: Boolean
        ): OperationalHour {
        return OperationalHour(
            sunday = if (copySunday) {
                this.sunday.deepCopy()
            } else {
                this.sunday
            },
            saturday = if (copySaturday) {
                this.saturday.deepCopy()
            } else {
                this.saturday
            },
            friday = if (copyFriday) {
                this.friday.deepCopy()
            } else {
                this.friday
            },
            thursday = if (copyThursday) {
                this.thursday.deepCopy()
            } else {
                this.thursday
            },
            wednesday = if (copyWednesday) {
                this.wednesday.deepCopy()
            } else {
                this.wednesday
            },
            tuesday = if (copyTuesday) {
                this.tuesday.deepCopy()
            } else {
                this.tuesday
            },
            monday = if (copyMonday) {
                this.monday.deepCopy()
            } else {
                this.monday
            }
        )
    }

}

@Parcelize
data class DailySchedule(
    @get:PropertyName("open") @set:PropertyName("open") var open: String = "",
    @get:PropertyName("close") @set:PropertyName("close") var close: String = ""
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    fun deepCopy(): DailySchedule {
        return DailySchedule(
            open = this.open,
            close = this.close
        )
    }
}

// Bundling package data class
@Parcelize
data class BundlingPackage(
    @get:PropertyName("accumulated_price") @set:PropertyName("accumulated_price") var accumulatedPrice: Int = 0,
    @get:PropertyName("apply_to_general") @set:PropertyName("apply_to_general") var applyToGeneral: Boolean = true,
    @get:PropertyName("auto_selected") @set:PropertyName("auto_selected") var autoSelected: Boolean = false,
    @get:PropertyName("default_item") @set:PropertyName("default_item") var defaultItem: Boolean = false,
    @get:PropertyName("list_items") @set:PropertyName("list_items") var listItems: @RawValue List<String> = emptyList(),
    @get:PropertyName("package_counting") @set:PropertyName("package_counting") var packageCounting: Int = 0,
    @get:PropertyName("package_desc") @set:PropertyName("package_desc") var packageDesc: String = "",
    @get:PropertyName("package_discount") @set:PropertyName("package_discount") var packageDiscount: Int = 0,
    @get:PropertyName("package_name") @set:PropertyName("package_name") var packageName: String = "",
    @get:PropertyName("package_price") @set:PropertyName("package_price") var packagePrice: Int = 0,
    @get:PropertyName("package_rating") @set:PropertyName("package_rating") var packageRating: Double = 5.0,
    @get:PropertyName("results_share_amount") @set:PropertyName("results_share_amount") var resultsShareAmount: @RawValue Map<String, Int>? = emptyMap(),
    @get:PropertyName("results_share_format") @set:PropertyName("results_share_format") var resultsShareFormat: String = "",
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:Exclude @set:Exclude var priceToDisplay: Int = 0,
    @get:Exclude @set:Exclude var listItemDetails: @RawValue List<Service>? = null,
    @get:Exclude @set:Exclude var bundlingQuantity: Int = 0,
    @get:Exclude @set:Exclude var itemIndex: Int = 0,
    @get:Exclude @set:Exclude var dataRef: String = ""
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    // Deep copy function for BundlingPackage
    fun deepCopy(deepCopyItemsDetails: Boolean): BundlingPackage {
        return BundlingPackage(
            accumulatedPrice = this.accumulatedPrice,
            applyToGeneral = this.applyToGeneral,
            autoSelected = this.autoSelected,
            defaultItem = this.defaultItem,
            listItems = this.listItems.toList(),
            packageCounting = this.packageCounting,
//            packageCounting = this.packageCounting?.toMap(),
            packageDesc = this.packageDesc,
            packageDiscount = this.packageDiscount,
            packageName = this.packageName,
            packagePrice = this.packagePrice,
            packageRating = this.packageRating,
            resultsShareAmount = this.resultsShareAmount?.toMap(),
            resultsShareFormat = this.resultsShareFormat,
            rootRef = this.rootRef,
            uid = this.uid,
            priceToDisplay = this.priceToDisplay,
            listItemDetails = if (deepCopyItemsDetails) {
                this.listItemDetails?.map { it.deepCopy() } // Deep copy each Service
            } else {
                this.listItemDetails // Copy references
            },
            bundlingQuantity = this.bundlingQuantity,
            itemIndex = this.itemIndex,
            dataRef = this.dataRef
        )
    }

}


// Division data class
@Parcelize
data class Division(
    @get:PropertyName("division_description") @set:PropertyName("division_description") var divisionDescription: String = "",
    @get:PropertyName("division_name") @set:PropertyName("division_name") var divisionName: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0
}

// Employee data class
@Parcelize
data class UserEmployeeData(
    @get:PropertyName("accumulated_lateness") @set:PropertyName("accumulated_lateness") var accumulatedLateness: @RawValue Map<String, Int>? = emptyMap(),
    // @get:PropertyName("amount_of_bon") @set:PropertyName("amount_of_bon") var amountOfBon: Int = 0,
    // @get:PropertyName("appointment_list") @set:PropertyName("appointment_list") var appointmentList: MutableList<ListStackData>? = null,
    @get:PropertyName("user_reminder") @set:PropertyName("user_reminder") var userReminder: MutableList<NotificationReminder>? = null,
    @get:PropertyName("availability_status") @set:PropertyName("availability_status") var availabilityStatus: Boolean = true,
    @get:PropertyName("customer_counting") @set:PropertyName("customer_counting") var customerCounting: Int = 0,
    @get:PropertyName("email") @set:PropertyName("email") override var email: String = "",
    @get:PropertyName("employee_rating") @set:PropertyName("employee_rating") var employeeRating: Double = 5.0,
    @get:PropertyName("fullname") @set:PropertyName("fullname") var fullname: String = "",
    @get:PropertyName("gender") @set:PropertyName("gender") var gender: String = "",
    @get:PropertyName("uid_list_placement") @set:PropertyName("uid_list_placement") var uidListPlacement: List<String> = emptyList(),
    @get:PropertyName("password") @set:PropertyName("password") override var password: String = "",
    @get:PropertyName("phone") @set:PropertyName("phone") override var phone: String = "",
    @get:PropertyName("photo_profile") @set:PropertyName("photo_profile") var photoProfile: String = "",
    @get:PropertyName("pin") @set:PropertyName("pin") var pin: String = "",
    @get:PropertyName("point") @set:PropertyName("point") var point: Int = 0,
    @get:PropertyName("positions") @set:PropertyName("positions") var positions: String = "",
    @get:PropertyName("role") @set:PropertyName("role") var role: String = "",
    @get:PropertyName("role_detail") @set:PropertyName("role_detail") var roleDetail: String = "",
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("salary") @set:PropertyName("salary") var salary: Int = 0,
    @get:PropertyName("uid") @set:PropertyName("uid") override var uid: String = "----------------",
    @get:PropertyName("username") @set:PropertyName("username") var username: String = "",
    @get:PropertyName("user_notification") @set:PropertyName("user_notification") var userNotification: MutableList<NotificationReminder>? = null,
    @get:Exclude @set:Exclude override var userRef: String = "",
    @get:Exclude @set:Exclude var outletRef: String = "",
    @get:Exclude @set:Exclude var restOfQueue: Int = 0,
    // @get:Exclude @set:Exclude var outletPlacement : List<Outlet>? = null,
//    @get:PropertyName("service_commission") @set:PropertyName("service_commission") var serviceCommission: @RawValue Map<String, Int> = emptyMap(),
//    @get:PropertyName("product_commission") @set:PropertyName("product_commission") var productCommission: @RawValue Map<String, Int> = emptyMap(),
//    @get:PropertyName("specialization_cost") @set:PropertyName("specialization_cost") var specializationCost: Int = 0,
//    @get:PropertyName("rest_queue_counting") @set:PropertyName("rest_queue_counting") var restQueueCounting: Int = 0,
//    @get:PropertyName("user_review_counting") @set:PropertyName("user_review_counting") var userReviewCounting: Int = 0
) : Parcelable, UserData {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    // Deep copy function for Employee
    fun deepCopy(copyReminder: Boolean, copyNotification: Boolean): UserEmployeeData {
        return UserEmployeeData(
            accumulatedLateness = this.accumulatedLateness?.toMap(),
//            amountOfBon = this.amountOfBon,
//            appointmentList = if (copyAppointments) {
//                this.appointmentList?.map { it.deepCopy() }?.toMutableList()
//            } else {
//                this.appointmentList // Copy reference
//            },
            userReminder = if (copyReminder) {
                this.userReminder?.map { it.deepCopy() }?.toMutableList()
            } else {
                this.userReminder // Copy reference
            },
            availabilityStatus = this.availabilityStatus,
            customerCounting = this.customerCounting,
//            customerCounting = this.customerCounting?.toMap(),
            email = this.email,
            employeeRating = this.employeeRating,
            fullname = this.fullname,
            gender = this.gender,
            uidListPlacement = this.uidListPlacement.toList(),
            password = this.password,
            phone = this.phone,
            photoProfile = this.photoProfile,
            pin = this.pin,
            point = this.point,
            positions = this.positions,
            role = this.role,
            roleDetail = this.roleDetail,
            rootRef = this.rootRef,
            salary = this.salary,
            uid = this.uid,
            username = this.username,
            userNotification = if (copyNotification) {
                this.userNotification?.map { it.deepCopy() }?.toMutableList()
            } else {
                this.userNotification // Copy reference
            },
            userRef = this.userRef,
            outletRef = this.outletRef,
            restOfQueue = this.restOfQueue
        )
    }

}

@Parcelize
data class AttendanceRecord(
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0
}

@Parcelize
data class LeavePermission(
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0
}

// Outlet data class
@Parcelize
data class Outlet(
    @get:PropertyName("active_devices") @set:PropertyName("active_devices") var activeDevices: Int = 0,
    @get:PropertyName("current_queue") @set:PropertyName("current_queue") var currentQueue: @RawValue Map<String, String>? = emptyMap(),
    @get:PropertyName("img_outlet") @set:PropertyName("img_outlet") var imgOutlet: String = "",
    @get:PropertyName("last_updated") @set:PropertyName("last_updated") var lastUpdated: Timestamp = Timestamp.now(),
    @get:PropertyName("list_best_deals") @set:PropertyName("list_best_deals") var listBestDeals: @RawValue List<String> = emptyList(),
    @get:PropertyName("list_bundling") @set:PropertyName("list_bundling") var listBundling: @RawValue List<String> = emptyList(),
    @get:PropertyName("list_customers") @set:PropertyName("list_customers") var listCustomers: MutableList<Customer>? = null,
    @get:PropertyName("list_employees") @set:PropertyName("list_employees") var listEmployees: @RawValue List<String> = emptyList(),
    @get:PropertyName("list_products") @set:PropertyName("list_products") var listProducts: @RawValue List<String> = emptyList(),
    @get:PropertyName("list_services") @set:PropertyName("list_services") var listServices: @RawValue List<String> = emptyList(),
    @get:PropertyName("open_status") @set:PropertyName("open_status") var openStatus: Boolean = false,
    @get:PropertyName("outlet_access_code") @set:PropertyName("outlet_access_code") var outletAccessCode: String = "",
    @get:PropertyName("outlet_name") @set:PropertyName("outlet_name") var outletName: String = "",
    @get:PropertyName("outlet_address") @set:PropertyName("outlet_address") var outletAddress: String = "",
    @get:PropertyName("latitude_point") @set:PropertyName("latitude_point") var latitudePoint: Double = 0.0,
    @get:PropertyName("longitude_point") @set:PropertyName("longitude_point") var longitudePoint: Double = 0.0,
    @get:PropertyName("outlet_phone_number") @set:PropertyName("outlet_phone_number") var outletPhoneNumber: String = "",
    @get:PropertyName("outlet_rating") @set:PropertyName("outlet_rating") var outletRating: Double = 5.0,
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("tagline_or_desc") @set:PropertyName("tagline_or_desc") var taglineOrDesc: String = "",
    @get:PropertyName("timestamp_modify") @set:PropertyName("timestamp_modify") var timestampModify: Timestamp = Timestamp.now(),
    @get:PropertyName("hidden_outlet") @set:PropertyName("hidden_outlet") var hiddenOutlet: Boolean = false,
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:Exclude @set:Exclude var isCollapseCard: Boolean = true,
    @get:Exclude @set:Exclude var outletReference: String = "",
    //    @get:PropertyName("status_active") @set:PropertyName("status_active") var statusActive: Boolean = false,
//    var dailyCapitalIsEmpty: Boolean = true,
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    fun deepCopy(): Outlet {
        return Outlet(
            activeDevices = this.activeDevices,
            currentQueue = this.currentQueue?.toMap(), // salin map
            imgOutlet = this.imgOutlet,
            lastUpdated = this.lastUpdated, // Timestamp immutable (Firestore)
            listBestDeals = this.listBestDeals.toList(),
            listBundling = this.listBundling.toList(),
            listCustomers = this.listCustomers?.map { it.copy() }?.toMutableList(),
            listEmployees = this.listEmployees.toList(),
            listProducts = this.listProducts.toList(),
            listServices = this.listServices.toList(),
            openStatus = this.openStatus,
            outletAccessCode = this.outletAccessCode,
            outletName = this.outletName,
            outletAddress = this.outletAddress,
            latitudePoint = this.latitudePoint,
            longitudePoint = this.longitudePoint,
            outletPhoneNumber = this.outletPhoneNumber,
            outletRating = this.outletRating,
            rootRef = this.rootRef,
            taglineOrDesc = this.taglineOrDesc,
            timestampModify = this.timestampModify,
            hiddenOutlet = this.hiddenOutlet,
            uid = this.uid,
            isCollapseCard = this.isCollapseCard,
            outletReference = this.outletReference
        )
    }

}

@Parcelize
data class Customer(
    @get:PropertyName("last_reserve") @set:PropertyName("last_reserve") var lastReserve: Timestamp = Timestamp.now(),
    @get:PropertyName("uid_customer") @set:PropertyName("uid_customer") var uidCustomer: String = ""
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0
}

// Product data class
@Parcelize
data class Product(
    @get:PropertyName("apply_to_general") @set:PropertyName("apply_to_general") var applyToGeneral: Boolean = true,
    @get:PropertyName("category_detail") @set:PropertyName("category_detail") var categoryDetail: String = "",
    @get:PropertyName("img_product") @set:PropertyName("img_product") var imgProduct: String = "",
    @get:PropertyName("product_category") @set:PropertyName("product_category") var productCategory: String = "",
    @get:PropertyName("product_counting") @set:PropertyName("product_counting") var productCounting: Int = 0,
    @get:PropertyName("product_description") @set:PropertyName("product_description") var productDescription: String = "",
    @get:PropertyName("product_name") @set:PropertyName("product_name") var productName: String = "",
    @get:PropertyName("product_price") @set:PropertyName("product_price") var productPrice: Int = 0,
    @get:PropertyName("product_rating") @set:PropertyName("product_rating") var productRating: Double = 4.5,
    @get:PropertyName("results_share_amount") @set:PropertyName("results_share_amount") var resultsShareAmount: @RawValue Map<String, Int>? = emptyMap(),
    @get:PropertyName("results_share_format") @set:PropertyName("results_share_format") var resultsShareFormat: String = "",
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("seller") @set:PropertyName("seller") var seller: @RawValue Seller = Seller(),
    @get:PropertyName("stock_quantity") @set:PropertyName("stock_quantity") var stockQuantity: Int = 0,
    @get:PropertyName("tag") @set:PropertyName("tag") var tag: @RawValue List<String> = emptyList(),
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:Exclude @set:Exclude var purchasePrice: String = "",
    @get:Exclude @set:Exclude var numberOfSales: Int = 0,
    @get:Exclude @set:Exclude var dataRef: String = ""
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    fun deepCopy(
        copyDataSeller: Boolean
    ): Product {
        return Product(
            applyToGeneral = this.applyToGeneral,
            categoryDetail = this.categoryDetail,
            imgProduct = this.imgProduct,
            productCategory = this.productCategory,
            productCounting = this.productCounting,
            productDescription = this.productDescription,
            productName = this.productName,
            productPrice = this.productPrice,
            productRating = this.productRating,
            resultsShareAmount = this.resultsShareAmount?.toMap(),
            resultsShareFormat = this.resultsShareFormat,
            rootRef = this.rootRef,
            seller = if (copyDataSeller) {
                this.seller.deepCopy()
            } else {
                this.seller
            },
            stockQuantity = this.stockQuantity,
            tag = this.tag.toList(),
            uid = this.uid,
            purchasePrice = this.purchasePrice,
            numberOfSales = this.numberOfSales,
            dataRef = this.dataRef
        )
    }
}

@Parcelize
data class Seller(
    @get:PropertyName("origin_location") @set:PropertyName("origin_location") var originLocation: String = "",
    @get:PropertyName("location_point") @set:PropertyName("location_point") var locationPoint: LocationPoint? = null,
    @get:PropertyName("seller_name") @set:PropertyName("seller_name") var sellerName: String = "",
    @get:PropertyName("seller_phone") @set:PropertyName("seller_phone") var sellerPhone: String = "",
    @get:PropertyName("seller_profile") @set:PropertyName("seller_profile") var sellerProfile: String = "",
    @get:PropertyName("uid_seller") @set:PropertyName("uid_seller") var uidSeller: String = "",
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    fun deepCopy(): Seller {
        return Seller(
            originLocation = this.originLocation,
            locationPoint = this.locationPoint?.deepCopy(),
            sellerName = this.sellerName,
            sellerPhone = this.sellerPhone,
            sellerProfile = this.sellerProfile,
            uidSeller = this.uidSeller
        )
    }
}

// Role data class
@Parcelize
data class Role(
    @get:PropertyName("job_desc") @set:PropertyName("job_desc") var jobDesc: String = "",
    @get:PropertyName("permissions") @set:PropertyName("permissions") var permissions: @RawValue Map<String, Boolean>? = emptyMap(),
    @get:PropertyName("role_name") @set:PropertyName("role_name") var roleName: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = ""
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0
}


// Service data class
@Parcelize
data class Service(
    @get:PropertyName("apply_to_general") @set:PropertyName("apply_to_general") var applyToGeneral: Boolean = true,
    @get:PropertyName("auto_selected") @set:PropertyName("auto_selected") var autoSelected: Boolean = false,
    @get:PropertyName("category_detail") @set:PropertyName("category_detail") var categoryDetail: String = "",
    @get:PropertyName("default_item") @set:PropertyName("default_item") var defaultItem: Boolean = false,
    @get:PropertyName("free_of_charge") @set:PropertyName("free_of_charge") var freeOfCharge: Boolean = false,
    @get:PropertyName("results_share_amount") @set:PropertyName("results_share_amount") var resultsShareAmount: @RawValue Map<String, Int>? = emptyMap(),
    @get:PropertyName("results_share_format") @set:PropertyName("results_share_format") var resultsShareFormat: String = "",
    @get:PropertyName("root_ref") @set:PropertyName("root_ref") var rootRef: String = "",
    @get:PropertyName("service_category") @set:PropertyName("service_category") var serviceCategory: String = "",
    @get:PropertyName("service_counting") @set:PropertyName("service_counting") var serviceCounting: Int = 0,
    @get:PropertyName("service_desc") @set:PropertyName("service_desc") var serviceDesc: String = "",
    @get:PropertyName("service_icon") @set:PropertyName("service_icon") var serviceIcon: String = "",
    @get:PropertyName("service_img") @set:PropertyName("service_img") var serviceImg: String = "",
    @get:PropertyName("service_name") @set:PropertyName("service_name") var serviceName: String = "",
    @get:PropertyName("service_price") @set:PropertyName("service_price") var servicePrice: Int = 0,
    @get:PropertyName("service_rating") @set:PropertyName("service_rating") var serviceRating: Double = 5.0,
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:Exclude @set:Exclude var priceToDisplay: Int = 0,
    @get:Exclude @set:Exclude var serviceQuantity: Int = 0,
    @get:Exclude @set:Exclude var itemIndex: Int = 0,
    @get:Exclude @set:Exclude var dataRef: String = ""
) : Parcelable {
    // Mencegah field stability ikut terserialisasi ke Firestore
    @get:Exclude
    val stability: Int
        get() = 0

    // Deep copy function for Employee
    fun deepCopy(): Service {
        return Service(
            applyToGeneral = this.applyToGeneral,
            autoSelected = this.autoSelected,
            categoryDetail = this.categoryDetail,
            defaultItem = this.defaultItem,
            freeOfCharge = this.freeOfCharge,
            resultsShareAmount = this.resultsShareAmount?.toMap(),
            resultsShareFormat = this.resultsShareFormat,
            rootRef = this.rootRef,
            serviceCategory = this.serviceCategory,
            serviceCounting = this.serviceCounting,
//            serviceCounting = this.serviceCounting?.toMap(),
            serviceDesc = this.serviceDesc,
            serviceIcon = this.serviceIcon,
            serviceImg = this.serviceImg,
            serviceName = this.serviceName,
            servicePrice = this.servicePrice,
            serviceRating = this.serviceRating,
            uid = this.uid,
            priceToDisplay = this.priceToDisplay,
            serviceQuantity = this.serviceQuantity,
            itemIndex = this.itemIndex,
            dataRef = this.dataRef
        )
    }
}

