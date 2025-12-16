package com.example.demobase.service;

import com.example.demobase.dto.GameResponseDTO;
import com.example.demobase.model.GameInProgress;
import com.example.demobase.model.Player;
import com.example.demobase.model.Word;
import com.example.demobase.repository.GameInProgressRepository;
import com.example.demobase.repository.GameRepository;
import com.example.demobase.repository.PlayerRepository;
import com.example.demobase.repository.WordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameInProgressRepository gameInProgressRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private WordRepository wordRepository;

    @InjectMocks
    private GameService gameService;

    private Player player;
    private Word word;

    @BeforeEach
    void setUp() {
        player = new Player(1L, "Juan Pérez", LocalDate.of(2025, 1, 15));
        word = new Word(1L, "PROGRAMADOR", false);
    }

    @Test
    void testStartGame_Success() {
        // DO: Implementar el test para testStartGame_Success
        Long playerId = 1L;

        Player player = new Player();
        player.setId(playerId);
        player.setNombre("Pepe");

        Word word = new Word();
        word.setId(10L);
        word.setPalabra("CASA");
        word.setUtilizada(false);
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));

        when(wordRepository.findRandomWord()).thenReturn(Optional.of(word));

        when(gameInProgressRepository.findByJugadorIdOrderByFechaInicioDesc(playerId)).thenReturn(Collections.emptyList());
        when(wordRepository.save(any(Word.class))).thenAnswer(inv -> inv.getArgument(0));

        when(gameInProgressRepository.save(any(GameInProgress.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GameResponseDTO resp = gameService.startGame(playerId);
        assertNotNull(resp);
        ArgumentCaptor<Word> wordCaptor = ArgumentCaptor.forClass(Word.class);
        verify(wordRepository).save(wordCaptor.capture());
        assertTrue(wordCaptor.getValue().getUtilizada(), "La palabra debe quedar utilizada es deci debe quedar en true");
        ArgumentCaptor<GameInProgress> gipCaptor = ArgumentCaptor.forClass(GameInProgress.class);
        verify(gameInProgressRepository).save(gipCaptor.capture());

        GameInProgress saved = gipCaptor.getValue();
        assertEquals(playerId, saved.getJugador().getId());
        assertEquals(word.getId(), saved.getPalabra().getId());
        assertEquals(7, saved.getIntentosRestantes()); // o el valor que uses
        assertEquals("", saved.getLetrasIntentadas());
        assertNotNull(saved.getFechaInicio(), "fecha de Inicio no debe ser nulo");
        verify(wordRepository).findRandomWord();
        verify(gameInProgressRepository).findByJugadorIdOrderByFechaInicioDesc(playerId);
        verifyNoMoreInteractions(wordRepository, gameInProgressRepository);
    }

    @Test
    void testStartGame_PlayerNotFound() {
        // Given
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () -> gameService.startGame(999L));
        verify(playerRepository, times(1)).findById(999L);
        verify(wordRepository, never()).findRandomWord();
    }

    @Test
    void testStartGame_NoWordsAvailable() {
        // Given
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(wordRepository.findRandomWord()).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () -> gameService.startGame(1L));
        verify(playerRepository, times(1)).findById(1L);
        verify(wordRepository, times(1)).findRandomWord();
    }

    @Test
    void testStartGame_ExistingGameInProgress() {
        // Given
        GameInProgress existingGame = new GameInProgress();
        existingGame.setId(1L);
        existingGame.setJugador(player);
        existingGame.setPalabra(word);
        existingGame.setLetrasIntentadas("P,R");
        existingGame.setIntentosRestantes(5);
        existingGame.setFechaInicio(LocalDateTime.now());

        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(wordRepository.findRandomWord()).thenReturn(Optional.of(word));
        when(gameInProgressRepository.findByJugadorAndPalabra(1L, 1L)).thenReturn(Optional.of(existingGame));

        // When
        GameResponseDTO result = gameService.startGame(1L);

        // Then
        assertNotNull(result);
        assertEquals(5, result.getIntentosRestantes());
        verify(gameInProgressRepository, never()).save(any(GameInProgress.class));
    }

    @Test
    void testMakeGuess_Success_NewGame() {
        // Given - Primero necesitamos crear una partida en curso
        GameInProgress gameInProgress = new GameInProgress();
        gameInProgress.setId(1L);
        gameInProgress.setJugador(player);
        gameInProgress.setPalabra(word);
        gameInProgress.setLetrasIntentadas("");
        gameInProgress.setIntentosRestantes(7);
        gameInProgress.setFechaInicio(LocalDateTime.now());

        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(gameInProgressRepository.findByJugadorIdOrderByFechaInicioDesc(1L))
                .thenReturn(Arrays.asList(gameInProgress));
        when(gameInProgressRepository.save(any(GameInProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        GameResponseDTO result = gameService.makeGuess(1L, 'P');

        // Then
        assertNotNull(result);
        assertTrue(result.getPalabraOculta().contains("P"));
        assertTrue(result.getLetrasIntentadas().contains('P'));
        verify(gameInProgressRepository, times(1)).save(any(GameInProgress.class));
    }

    @Test
    void testMakeGuess_NoGameInProgress() {
        // Given
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(gameInProgressRepository.findByJugadorIdOrderByFechaInicioDesc(1L)).thenReturn(new ArrayList<>());

        // When & Then
        assertThrows(RuntimeException.class, () -> gameService.makeGuess(1L, 'P'));
        verify(gameInProgressRepository, times(1)).findByJugadorIdOrderByFechaInicioDesc(1L);
    }

    @Test
    void testMakeGuess_ExistingGame_CorrectLetter() {
        // Given
        GameInProgress gameInProgress = new GameInProgress();
        gameInProgress.setId(1L);
        gameInProgress.setJugador(player);
        gameInProgress.setPalabra(word);
        gameInProgress.setLetrasIntentadas("P");
        gameInProgress.setIntentosRestantes(7);
        gameInProgress.setFechaInicio(LocalDateTime.now());

        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(gameInProgressRepository.findByJugadorIdOrderByFechaInicioDesc(1L))
                .thenReturn(Arrays.asList(gameInProgress));
        when(gameInProgressRepository.save(any(GameInProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        GameResponseDTO result = gameService.makeGuess(1L, 'R');

        // Then
        assertNotNull(result);
        assertTrue(result.getLetrasIntentadas().contains('R'));
        assertEquals(7, result.getIntentosRestantes()); // No se descuenta porque la letra es correcta
        verify(gameInProgressRepository, times(1)).save(any(GameInProgress.class));
    }

    @Test
    void testMakeGuess_ExistingGame_IncorrectLetter() {
        // Given
        GameInProgress gameInProgress = new GameInProgress();
        gameInProgress.setId(1L);
        gameInProgress.setJugador(player);
        gameInProgress.setPalabra(word);
        gameInProgress.setLetrasIntentadas("P");
        gameInProgress.setIntentosRestantes(7);
        gameInProgress.setFechaInicio(LocalDateTime.now());

        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(gameInProgressRepository.findByJugadorIdOrderByFechaInicioDesc(1L))
                .thenReturn(Arrays.asList(gameInProgress));
        when(gameInProgressRepository.save(any(GameInProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        GameResponseDTO result = gameService.makeGuess(1L, 'X');

        // Then
        assertNotNull(result);
        assertTrue(result.getLetrasIntentadas().contains('X'));
        assertEquals(6, result.getIntentosRestantes()); // Se descuenta porque la letra es incorrecta
        verify(gameInProgressRepository, times(1)).save(any(GameInProgress.class));
    }

    @Test
    void testMakeGuess_DuplicateLetter() {
        // Given
        GameInProgress gameInProgress = new GameInProgress();
        gameInProgress.setId(1L);
        gameInProgress.setJugador(player);
        gameInProgress.setPalabra(word);
        gameInProgress.setLetrasIntentadas("P");
        gameInProgress.setIntentosRestantes(7);
        gameInProgress.setFechaInicio(LocalDateTime.now());

        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(gameInProgressRepository.findByJugadorIdOrderByFechaInicioDesc(1L))
                .thenReturn(Arrays.asList(gameInProgress));

        // When
        GameResponseDTO result = gameService.makeGuess(1L, 'P');

        // Then
        assertNotNull(result);
        assertEquals(7, result.getIntentosRestantes()); // No cambia porque la letra ya fue intentada
        verify(gameInProgressRepository, never()).save(any(GameInProgress.class));
    }
}

