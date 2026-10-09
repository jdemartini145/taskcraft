package pe.aphid.core.domain.pests

import pe.aphid.core.model.TODO_FUENTE

/** Ficha de manejo integrado de plagas (MIP). */
data class PestSheet(
    val id: String,
    val name: String,
    val scientificName: String,
    val identification: String,
    /** Umbral de acción; TODO_FUENTE = dato pendiente hasta tener fuente local verificada. */
    val threshold: String,
    val physicalControl: List<String>,
    val biologicalControl: List<String>,
    val reviewEveryDays: Int,
)

object PestCatalog {
    val sheets: List<PestSheet> = listOf(
        PestSheet(
            id = "mosquito_sustrato",
            name = "Mosquito del sustrato",
            scientificName = "Bradysia spp. (Sciaridae)",
            identification = "Mosquitas negras pequeñas que vuelan cerca del sustrato húmedo. Larvas blancas con cabeza negra que dañan raíces.",
            threshold = TODO_FUENTE,
            physicalControl = listOf(
                "Trampas cromáticas amarillas cerca de la superficie",
                "Reducir humedad superficial y cubrir el sustrato expuesto",
                "Eliminar restos vegetales y algas",
            ),
            biologicalControl = listOf(
                "Bacillus thuringiensis var. israelensis (Bti) en el agua de riego",
                "Ácaro depredador Stratiolaelaps scimitus",
                "Nematodos entomopatógenos Steinernema feltiae",
            ),
            reviewEveryDays = 7,
        ),
        PestSheet(
            id = "pulgon",
            name = "Pulgón",
            scientificName = "Aphididae (Myzus persicae, Aphis gossypii y otros)",
            identification = "Insectos blandos verdes, negros o amarillos agrupados en brotes y envés. Melaza pegajosa y hojas enrolladas.",
            threshold = TODO_FUENTE,
            physicalControl = listOf("Trampas amarillas", "Retirar brotes muy infestados", "Lavado con chorro de agua", "Malla antiinsectos en la entrada"),
            biologicalControl = listOf("Parasitoide Aphidius colemani", "Crisopas (Chrysoperla carnea)", "Mariquitas (Coccinellidae)"),
            reviewEveryDays = 3,
        ),
        PestSheet(
            id = "trips",
            name = "Trips",
            scientificName = "Frankliniella occidentalis y otros Thripidae",
            identification = "Insectos alargados de 1–2 mm. Manchas plateadas con puntos negros en hojas; flores deformadas.",
            threshold = TODO_FUENTE,
            physicalControl = listOf("Trampas cromáticas azules", "Malla antitrips", "Eliminar malezas alrededor"),
            biologicalControl = listOf("Ácaros depredadores Amblyseius (Neoseiulus) cucumeris y A. swirskii", "Chinche Orius spp."),
            reviewEveryDays = 3,
        ),
        PestSheet(
            id = "mosca_blanca",
            name = "Mosca blanca",
            scientificName = "Trialeurodes vaporariorum, Bemisia tabaci",
            identification = "Adultos blancos que vuelan al mover la planta; ninfas planas en el envés. Melaza y fumagina.",
            threshold = TODO_FUENTE,
            physicalControl = listOf("Trampas amarillas", "Malla antiinsectos", "Retirar hojas bajas con ninfas"),
            biologicalControl = listOf("Parasitoide Encarsia formosa", "Eretmocerus spp.", "Hongo Beauveria bassiana"),
            reviewEveryDays = 3,
        ),
        PestSheet(
            id = "arana_roja",
            name = "Araña roja",
            scientificName = "Tetranychus urticae",
            identification = "Ácaros diminutos en el envés; punteado amarillento en hojas y finas telarañas. Favorecida por ambiente cálido y seco.",
            threshold = TODO_FUENTE,
            physicalControl = listOf("Subir la humedad relativa", "Lavar el envés con agua", "Retirar hojas muy afectadas"),
            biologicalControl = listOf("Ácaro depredador Phytoseiulus persimilis", "Neoseiulus californicus", "Feltiella acarisuga"),
            reviewEveryDays = 3,
        ),
    )

    fun byId(id: String): PestSheet? = sheets.firstOrNull { it.id == id }
}
