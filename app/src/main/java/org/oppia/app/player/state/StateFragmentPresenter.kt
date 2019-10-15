package org.oppia.app.player.state

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import org.oppia.app.R
import org.oppia.app.customview.inputInteractionView.NumberInputInteractionView
import org.oppia.app.customview.inputInteractionView.TextInputInteractionView
import org.oppia.app.databinding.StateFragmentBinding
import org.oppia.app.fragment.FragmentScope
import org.oppia.app.model.CellularDataPreference
import org.oppia.app.model.EphemeralState
import org.oppia.app.player.audio.CellularDataDialogFragment
import org.oppia.app.viewmodel.ViewModelProvider
import org.oppia.domain.audio.CellularDialogController
import org.oppia.domain.exploration.ExplorationProgressController
import org.oppia.util.data.AsyncResult
import org.oppia.util.logging.Logger
import javax.inject.Inject

private const val TAG_CELLULAR_DATA_DIALOG = "CELLULAR_DATA_DIALOG"

/** The presenter for [StateFragment]. */
@FragmentScope
class StateFragmentPresenter @Inject constructor(
  private val fragment: Fragment,
  private val cellularDialogController: CellularDialogController,
  private val viewModelProvider: ViewModelProvider<StateViewModel>,
  private val explorationProgressController: ExplorationProgressController,
  private val logger: Logger
) {
  private var showCellularDataDialog = true
  private var useCellularData = false
  private lateinit var llRoot: LinearLayout
  private lateinit var dummyFetchButton: Button
  private lateinit var dummyFetchDataTV: TextView
  private lateinit var contentComponent: EditText
  private lateinit var digit: String
  private var isFetched: Boolean = false
  fun handleCreateView(inflater: LayoutInflater, container: ViewGroup?, digit: String, isFetched: Boolean): View? {
    cellularDialogController.getCellularDataPreference()
      .observe(fragment, Observer<AsyncResult<CellularDataPreference>> {
        if (it.isSuccess()) {
          val prefs = it.getOrDefault(CellularDataPreference.getDefaultInstance())
          showCellularDataDialog = !(prefs.hideDialog)
          useCellularData = prefs.useCellularData
        }
      })

    val binding = StateFragmentBinding.inflate(inflater, container, /* attachToRoot= */ false)
    binding.let {
      it.stateFragment = fragment as StateFragment
      it.viewModel = getStateViewModel()
    }
    this.digit = digit
    this.isFetched = isFetched
    subscribeToCurrentState()
    llRoot = binding.root.findViewById(R.id.llRoot)
    dummyFetchButton = binding.root.findViewById(R.id.dummy_fetch_button)
    dummyFetchDataTV = binding.root.findViewById(R.id.fetched_data_tv)
    return binding.root
  }

  fun handleAudioClick() {
    if (showCellularDataDialog) {
      setAudioFragmentVisible(false)
      showCellularDataDialogFragment()
    } else {
      setAudioFragmentVisible(useCellularData)
    }
  }

  fun getNumberTextInputText(): String {
    return contentComponent.text.toString()
  }

  fun isFetchClicked(): Boolean {
    return !dummyFetchDataTV.text.isEmpty()
  }

  fun handleEnableAudio(saveUserChoice: Boolean) {
    setAudioFragmentVisible(true)
    if (saveUserChoice)
      cellularDialogController.setAlwaysUseCellularDataPreference()
  }

  fun handleDisableAudio(saveUserChoice: Boolean) {
    if (saveUserChoice)
      cellularDialogController.setNeverUseCellularDataPreference()
  }

  private fun showCellularDataDialogFragment() {
    val previousFragment = fragment.childFragmentManager.findFragmentByTag(TAG_CELLULAR_DATA_DIALOG)
    if (previousFragment != null) {
      fragment.childFragmentManager.beginTransaction().remove(previousFragment).commitNow()
    }
    val dialogFragment = CellularDataDialogFragment.newInstance()
    dialogFragment.showNow(fragment.childFragmentManager, TAG_CELLULAR_DATA_DIALOG)
  }

  private fun getStateViewModel(): StateViewModel {
    return viewModelProvider.getForFragment(fragment, StateViewModel::class.java)
  }

  fun setAudioFragmentVisible(isVisible: Boolean) {
    getStateViewModel().setAudioFragmentVisible(isVisible)
  }

  private fun subscribeToCurrentState() {
    ephemeralStateLiveData.observe(fragment, Observer<EphemeralState> { result ->
      if (result.state.interaction.id == "NumericInput") {
        addNumberInputContentCard(result.state.interaction.customizationArgsMap.get("placeholder")!!.normalizedString)
      } else
        if (result.state.interaction.id == "TextInput") {
          addTextInputContentCard(result.state.interaction.customizationArgsMap.get("placeholder")!!.normalizedString)
        }
      logger.d("StateFragment", "getCurrentState: ${result.state.name}")
    })
  }

  /** The function for adding [TextInputInteractionView]. */
  private fun addTextInputContentCard(placeholder: String) {
    contentComponent = TextInputInteractionView(
      context = fragment.context!!,
      placeholder = placeholder
    )
    val params = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    )
    params.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
    llRoot.addView(contentComponent, params)
    contentComponent.setText(this.digit)
    if (isFetched) {
      dummyFetchDataTV.setText(digit)
    }
    dummyFetchButton.setOnClickListener({
      dummyFetchDataTV.setText(contentComponent.text)
    })
  }

  /** The function for adding [NumberInputInteractionView]. */
  private fun addNumberInputContentCard(placeholder: String) {
    contentComponent = NumberInputInteractionView(
      context = fragment.context!!,
      placeholder = placeholder
    )
    val params = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    )
    params.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
    llRoot.addView(contentComponent, params)
    contentComponent.setText(this.digit)
    if (isFetched) {
      dummyFetchDataTV.setText(digit)
    }
    this.dummyFetchButton.setOnClickListener(View.OnClickListener {
      this.dummyFetchDataTV.setText(contentComponent.text)

    })
  }

  private val ephemeralStateLiveData: LiveData<EphemeralState> by lazy {
    getEphemeralState()
  }

  private fun getEphemeralState(): LiveData<EphemeralState> {
    return Transformations.map(explorationProgressController.getCurrentState(), ::processCurrentState)
  }

  private fun processCurrentState(ephemeralStateResult: AsyncResult<EphemeralState>): EphemeralState {
    if (ephemeralStateResult.isFailure()) {
      logger.e("StateFragment", "Failed to retrieve ephemeral state", ephemeralStateResult.getErrorOrNull()!!)
    }
    return ephemeralStateResult.getOrDefault(EphemeralState.getDefaultInstance())
  }

  private fun dpToPx(dp: Int): Int {
    return (dp * Resources.getSystem().getDisplayMetrics().density).toInt()
  }
}
