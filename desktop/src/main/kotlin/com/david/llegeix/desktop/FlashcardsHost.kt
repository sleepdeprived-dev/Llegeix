package com.david.llegeix.desktop

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.david.llegeix.data.flashcards.StudyDirection
import com.david.llegeix.data.flashcards.StudyScope
import com.david.llegeix.ui.flashcards.CardEditorScreen
import com.david.llegeix.ui.flashcards.DeckScreen
import com.david.llegeix.ui.flashcards.FlashcardsScreen
import com.david.llegeix.ui.flashcards.StudyScreen
import com.david.llegeix.ui.flashcards.WeakWordsScreen

/**
 * The Targetes section on the Mac: the phone's own screens, wired together the
 * way the phone's AppNavigation wires them, route for route.
 */
@Composable
fun FlashcardsHost(navController: NavHostController) {
    NavHost(navController, startDestination = FLASHCARDS) {
        composable(FLASHCARDS) {
            FlashcardsScreen(
                onOpenDeck = { deckId -> navController.navigate("flashcards/deck/$deckId") },
                onOpenWeak = { navController.navigate(WEAK) },
                onStudy = { scope, direction -> navController.navigate(study(scope, direction)) },
            )
        }
        composable(
            route = STUDY,
            arguments = listOf(
                navArgument("deckId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("collectionId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("direction") { type = NavType.StringType; defaultValue = StudyDirection.Default.name },
                navArgument("weak") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val args = entry.arguments
            StudyScreen(
                scope = StudyScope.fromRoute(
                    deckId = args?.read { getLongOrNull("deckId") } ?: StudyScope.NONE,
                    collectionId = args?.read { getLongOrNull("collectionId") } ?: StudyScope.NONE,
                    weak = args?.read { getBooleanOrNull("weak") } ?: false,
                ),
                direction = StudyDirection.fromName(args?.read { getStringOrNull("direction") }),
                onBack = { navController.popBackStack() },
            )
        }
        composable(WEAK) {
            WeakWordsScreen(
                onBack = { navController.popBackStack() },
                onStudy = { scope, direction -> navController.navigate(study(scope, direction)) },
            )
        }
        composable(DECK, arguments = listOf(navArgument("deckId") { type = NavType.LongType })) { entry ->
            val deckId = entry.arguments?.read { getLongOrNull("deckId") } ?: 0L
            DeckScreen(
                deckId = deckId,
                onBack = { navController.popBackStack() },
                onAddCard = { navController.navigate(cardEditor(deckId)) },
                onEditCard = { cardId -> navController.navigate(cardEditor(deckId, cardId)) },
            )
        }
        composable(
            route = CARD_EDITOR,
            arguments = listOf(
                navArgument("deckId") { type = NavType.LongType },
                navArgument("cardId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            CardEditorScreen(
                deckId = entry.arguments?.read { getLongOrNull("deckId") } ?: 0L,
                cardId = entry.arguments?.read { getLongOrNull("cardId") }?.takeIf { it >= 0 },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private const val FLASHCARDS = "flashcards"
private const val DECK = "flashcards/deck/{deckId}"
private const val WEAK = "flashcards/weak"
private const val CARD_EDITOR = "flashcards/card?deckId={deckId}&cardId={cardId}"
private const val STUDY =
    "flashcards/study?deckId={deckId}&collectionId={collectionId}&direction={direction}&weak={weak}"

private fun cardEditor(deckId: Long, cardId: Long? = null) = "flashcards/card?deckId=$deckId&cardId=${cardId ?: -1}"

private fun study(scope: StudyScope, direction: StudyDirection) =
    "flashcards/study?deckId=${scope.deckArgument}&collectionId=${scope.collectionArgument}" +
        "&direction=${direction.name}&weak=${scope == StudyScope.Weak}"
