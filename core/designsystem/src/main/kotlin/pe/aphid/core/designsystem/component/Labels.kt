package pe.aphid.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pe.aphid.core.designsystem.R
import pe.aphid.core.model.GrowSystemType
import pe.aphid.core.model.GrowthStage

/** Etiquetas localizadas de enums del modelo (el modelo guarda nombres neutrales). */
@Composable
fun stageLabels(): Map<GrowthStage, String> = mapOf(
    GrowthStage.PLANTULA to stringResource(R.string.stage_plantula),
    GrowthStage.VEGETATIVA to stringResource(R.string.stage_vegetativa),
    GrowthStage.FLORACION to stringResource(R.string.stage_floracion),
    GrowthStage.FRUCTIFICACION to stringResource(R.string.stage_fructificacion),
)

@Composable
fun systemTypeLabels(): Map<GrowSystemType, String> = mapOf(
    GrowSystemType.DWC to stringResource(R.string.systype_dwc),
    GrowSystemType.NFT to stringResource(R.string.systype_nft),
    GrowSystemType.KRATKY to stringResource(R.string.systype_kratky),
    GrowSystemType.TORRE_VERTICAL to stringResource(R.string.systype_tower),
    GrowSystemType.GOTEO to stringResource(R.string.systype_drip),
    GrowSystemType.FLUJO_REFLUJO to stringResource(R.string.systype_ebb),
    GrowSystemType.RAIZ_FLOTANTE to stringResource(R.string.systype_raft),
)
