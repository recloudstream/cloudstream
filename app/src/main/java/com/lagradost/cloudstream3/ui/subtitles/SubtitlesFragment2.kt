package com.lagradost.cloudstream3.ui.subtitles

import com.lagradost.cloudstream3.utils.BaseComposeFragment
import com.lagradost.cloudstream3.utils.BaseDialogComposeFragment
import com.lagradost.cloudstream4.compose.Screen

class SubtitlesFragment2 : BaseComposeFragment(), Screen by SubtitlesScreen
class SubtitlesFragmentDialog2 : BaseDialogComposeFragment(), Screen by SubtitlesScreen