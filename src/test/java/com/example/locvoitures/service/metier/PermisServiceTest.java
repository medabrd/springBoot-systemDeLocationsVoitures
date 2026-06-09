package com.example.locvoitures.service.metier;

import com.example.locvoitures.entity.Client;
import com.example.locvoitures.entity.Permis;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.repository.ClientRepository;
import com.example.locvoitures.repository.PermisRepository;
import com.example.locvoitures.service.utils.EmailService;
import com.example.locvoitures.service.utils.FileStorageService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PermisServiceTest {

    @Mock private PermisRepository permisRepo;
    @Mock private ClientRepository clientRepo;
    @Mock private FileStorageService fileStorage;
    @Mock private EmailService emailService;

    @InjectMocks private PermisService permisService;

    @Test
    void mettreAJourPourClient_clientSansPermis_dateSansNumero_doitLeverBusinessException() {
        Client client = new Client();

        assertThatThrownBy(() -> permisService.mettreAJourPourClient(
                client,
                null,
                LocalDate.of(2020, 1, 1),
                null,
                null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Numero de permis requis");

        assertThat(client.getPermis()).isNull();
        verify(permisRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mettreAJourPourClient_clientSansPermis_toutVide_doitNeRienFaire() {
        Client client = new Client();

        permisService.mettreAJourPourClient(client, null, null, null, null);

        assertThat(client.getPermis()).isNull();
    }

    @Test
    void mettreAJourPourClient_clientSansPermis_avecNumero_doitCreerPermis() {
        Client client = new Client();
        lenient().when(permisRepo.findByNumero(anyString())).thenReturn(Optional.empty());

        permisService.mettreAJourPourClient(
                client,
                "PRM-12345",
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2030, 1, 1),
                null);

        assertThat(client.getPermis()).isNotNull();
        assertThat(client.getPermis().getNumero()).isEqualTo("PRM-12345");
        assertThat(client.getPermis().getDateObtention()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(client.getPermis().getDateExpiration()).isEqualTo(LocalDate.of(2030, 1, 1));
    }

    @Test
    void mettreAJourPourClient_clientAvecPermisExistant_peutMettreAJourDateSansNumero() {
        Client client = new Client();
        Permis permisExistant = Permis.builder().numero("DEJA-1").build();
        client.setPermis(permisExistant);

        permisService.mettreAJourPourClient(
                client,
                null,
                LocalDate.of(2021, 6, 1),
                null,
                null);

        assertThat(client.getPermis()).isSameAs(permisExistant);
        assertThat(client.getPermis().getNumero()).isEqualTo("DEJA-1");
        assertThat(client.getPermis().getDateObtention()).isEqualTo(LocalDate.of(2021, 6, 1));
    }
}
