package com.school.pdf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le cartouche du logo affiche soit les initiales de l'etablissement, soit — si
 * aucun nom n'est configure — celles de la marque.
 * <p>
 * Ce test existe parce que la marque s'appelle « Sahel Edu » : prendre naivement
 * ses deux premiers caracteres donnerait « SA » au lieu de « SE ».
 */
class SchoolDocumentThemeInitialsTest {

    @Test
    void aucunNomConfigure_afficheLesInitialesDeLaMarque() {
        assertThat(SchoolDocumentTheme.initialsFor(null)).isEqualTo("SE");
        assertThat(SchoolDocumentTheme.initialsFor("")).isEqualTo("SE");
        assertThat(SchoolDocumentTheme.initialsFor("   ")).isEqualTo("SE");
        assertThat(SchoolDocumentTheme.initialsFor("\t")).isEqualTo("SE");
    }

    @Test
    void laMarqueNeSeConfondPasAvecSesDeuxPremiersCaracteres() {
        // Garde-fou de regression : « Sahel Edu ».substring(0, 2) == "SA".
        assertThat(SchoolDocumentTheme.BRAND_INITIALS)
                .isEqualTo("SE")
                .isNotEqualTo("Sahel Edu".substring(0, 2).toUpperCase());
    }

    @Test
    void nomEtablissementConfigure_conserveLesDeuxPremieresLettres() {
        assertThat(SchoolDocumentTheme.initialsFor("École Les Palmiers")).isEqualTo("ÉC");
        assertThat(SchoolDocumentTheme.initialsFor("Lycée Moderne")).isEqualTo("LY");
        assertThat(SchoolDocumentTheme.initialsFor("Université de Bamako")).isEqualTo("UN");
    }

    @Test
    void nomEntoureDEspaces_estRogneAvantDExtraireLesInitiales() {
        assertThat(SchoolDocumentTheme.initialsFor("  Lycée Moderne  ")).isEqualTo("LY");
    }

    @Test
    void nomDUneSeuleLettre_neProvoquePasDHorsBornes() {
        assertThat(SchoolDocumentTheme.initialsFor("A")).isEqualTo("A");
    }
}
