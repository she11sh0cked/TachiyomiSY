package eu.kanade.tachiyomi.ui.category.biometric

import android.os.Bundle
import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.plusAssign
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [BiometricTimesController]. Used to manage the categories of the library.
 */
@OptIn(ExperimentalTime::class)
class BiometricTimesPresenter : BasePresenter<BiometricTimesController>() {

    /**
     * List containing categories.
     */
    private var timeRanges: List<TimeRange> = emptyList()

    val preferences: PreferencesHelper = Injekt.get()

    val scope = CoroutineScope(Job() + Dispatchers.Main)

    /**
     * Called when the presenter is created.
     *
     * @param savedState The saved state of this presenter.
     */
    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        preferences.biometricTimeRanges().asFlow().onEach { prefTimeRanges ->
            timeRanges = prefTimeRanges.toList()
                .mapNotNull { TimeRange.fromPreferenceString(it) }.onEach { XLog.nst().d(it) }

            Observable.just(timeRanges)
                .map { it.map(::BiometricTimesItem) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeLatestCache(BiometricTimesController::setBiometricTimeItems)
        }.launchIn(scope)
    }

    /**
     * Creates and adds a new category to the database.
     *
     * @param name The name of the category to create.
     */
    fun createTimeRange(timeRange: TimeRange) {
        // Do not allow duplicate categories.
        if (timeRangeConflicts(timeRange)) {
            Observable.just(Unit).subscribeFirst({ view, _ -> view.onTimeRangeConflictsError() })
            return
        }

        XLog.nst().d(timeRange)

        preferences.biometricTimeRanges() += timeRange.toPreferenceString()
    }

    /**
     * Deletes the given categories from the database.
     *
     * @param timeRanges The list of categories to delete.
     */
    fun deleteTimeRanges(timeRanges: List<TimeRange>) {
        preferences.biometricTimeRanges().set(
            this.timeRanges.filterNot { it in timeRanges }.map { it.toPreferenceString() }.toSet()
        )
    }

    /**
     * Returns true if a category with the given name already exists.
     */
    private fun timeRangeConflicts(timeRange: TimeRange): Boolean {
        return timeRanges.any { timeRange.conflictsWith(it) }
    }
}