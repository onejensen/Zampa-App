import XCTest
@testable import Zampa

final class RecurringDaysTests: XCTestCase {

    // MARK: - isVisibleOnDay

    func testNonPermanentAlwaysVisible() {
        let menu = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                        isPermanent: false)
        XCTAssertTrue(menu.isVisibleOnDay(0))
        XCTAssertTrue(menu.isVisibleOnDay(3))
        XCTAssertTrue(menu.isVisibleOnDay(6))
    }

    func testPermanentWithoutDaysAlwaysVisible() {
        let menu = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                        isPermanent: true, recurringDays: nil)
        XCTAssertTrue(menu.isVisibleOnDay(0))
        XCTAssertTrue(menu.isVisibleOnDay(6))
    }

    func testPermanentWithEmptyDaysAlwaysVisible() {
        let menu = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                        isPermanent: true, recurringDays: [])
        XCTAssertTrue(menu.isVisibleOnDay(0))
    }

    func testPermanentOnlyVisibleOnSelectedDays() {
        let menu = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                        isPermanent: true, recurringDays: [3])
        XCTAssertFalse(menu.isVisibleOnDay(0))
        XCTAssertFalse(menu.isVisibleOnDay(2))
        XCTAssertTrue(menu.isVisibleOnDay(3))
        XCTAssertFalse(menu.isVisibleOnDay(4))
    }

    func testPermanentMultipleDays() {
        let menu = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                        isPermanent: true, recurringDays: [0, 2, 4])
        XCTAssertTrue(menu.isVisibleOnDay(0))
        XCTAssertFalse(menu.isVisibleOnDay(1))
        XCTAssertTrue(menu.isVisibleOnDay(2))
        XCTAssertFalse(menu.isVisibleOnDay(3))
        XCTAssertTrue(menu.isVisibleOnDay(4))
        XCTAssertFalse(menu.isVisibleOnDay(5))
        XCTAssertFalse(menu.isVisibleOnDay(6))
    }

    // MARK: - occupiedDays(from:)

    func testOccupiedDaysEmpty() {
        XCTAssertEqual(Menu.occupiedDays(from: []), Set())
    }

    func testOccupiedDaysFromSingleOffer() {
        let menu = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                        isPermanent: true, recurringDays: [1, 3, 5])
        XCTAssertEqual(Menu.occupiedDays(from: [menu]), Set([1, 3, 5]))
    }

    func testOccupiedDaysFromMultipleOffers() {
        let m1 = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                      isPermanent: true, recurringDays: [0, 1])
        let m2 = Menu(id: "2", businessId: "b", date: "", title: "T", priceTotal: 5,
                      isPermanent: true, recurringDays: [4, 5])
        XCTAssertEqual(Menu.occupiedDays(from: [m1, m2]), Set([0, 1, 4, 5]))
    }

    func testOccupiedDaysLegacyPermanentOccupiesAll() {
        let menu = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                        isPermanent: true, recurringDays: nil)
        XCTAssertEqual(Menu.occupiedDays(from: [menu]), Set(0...6))
    }

    func testOccupiedDaysLegacyEmptyOccupiesAll() {
        let menu = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                        isPermanent: true, recurringDays: [])
        XCTAssertEqual(Menu.occupiedDays(from: [menu]), Set(0...6))
    }
}
