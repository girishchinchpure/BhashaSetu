from typing import Any, Dict, List


# ---------------------------------------------------------
# Number words used by the first prototype activity set.
# These are Hindi values that will later be passed through
# the Hindi -> Santhali translation system.
# ---------------------------------------------------------

NUMBER_WORDS_HINDI = {
    1: "एक",
    2: "दो",
    3: "तीन",
    4: "चार",
    5: "पाँच",
    6: "छह",
    7: "सात",
    8: "आठ",
    9: "नौ",
    10: "दस",
}


def generate_worksheet(
    class_name: str,
    subject: str,
    topics: List[Dict[str, str]],
    difficulty: str,
) -> Dict[str, Any]:
    """
    Generate structured worksheet content.

    This first version uses controlled educational templates.
    Later, the Hindi content can be passed to the translation
    engine and then rendered into a PDF.
    """

    activities = []

    for topic_item in topics:
        topic = topic_item["topic"]
        date = topic_item["date"]

        activity = create_activity(
            topic=topic,
            date=date,
            difficulty=difficulty,
        )

        activities.append(activity)

    return {
        "type": "worksheet",
        "class": class_name,
        "subject": subject,
        "difficulty": difficulty,
        "activities": activities,
    }


def generate_flashcards(
    class_name: str,
    subject: str,
    topics: List[Dict[str, str]],
    difficulty: str,
) -> Dict[str, Any]:
    """
    Generate structured flashcard content.
    """

    flashcards = []

    for topic_item in topics:
        topic = topic_item["topic"]
        date = topic_item["date"]

        cards = create_flashcards(
            topic=topic,
            date=date,
            difficulty=difficulty,
        )

        flashcards.extend(cards)

    return {
        "type": "flashcards",
        "class": class_name,
        "subject": subject,
        "difficulty": difficulty,
        "cards": flashcards,
    }


def create_activity(
    topic: str,
    date: str,
    difficulty: str,
) -> Dict[str, Any]:
    """
    Create an activity based on the topic and difficulty.
    """

    normalized_topic = topic.lower().strip()

    if "number" in normalized_topic:
        return create_number_activity(
            topic=topic,
            date=date,
            difficulty=difficulty,
        )

    # Generic fallback activity.
    return {
        "topic": topic,
        "date": date,
        "activity_type": "practice",
        "instruction_hi": (
            f"आज पढ़ाए गए विषय '{topic}' का अभ्यास कीजिए।"
        ),
        "difficulty": difficulty,
        "questions": [
            {
                "question_hi": (
                    f"'{topic}' से संबंधित सही उत्तर चुनिए।"
                ),
                "options_hi": [
                    "विकल्प 1",
                    "विकल्प 2",
                    "विकल्प 3",
                ],
            }
        ],
    }


def create_number_activity(
    topic: str,
    date: str,
    difficulty: str,
) -> Dict[str, Any]:
    """
    Generate number-learning activities for Numbers 1-10.
    """

    if difficulty == "Easy":
        questions = [
            {
                "question_hi": "1 से 5 तक की संख्याएँ सही क्रम में लिखिए।",
                "answer_hi": "1, 2, 3, 4, 5",
            },
            {
                "question_hi": "खाली स्थान भरिए: 1, 2, __, 4, 5",
                "answer_hi": "3",
            },
            {
                "question_hi": "सही संख्या पर गोला लगाइए: 3   7   9",
                "answer_hi": "3",
            },
        ]

    elif difficulty == "Medium":
        questions = [
            {
                "question_hi": "खाली स्थान भरिए: 1, 2, __, 4, __, 6",
                "answer_hi": "3, 5",
            },
            {
                "question_hi": "कौन-सी संख्या 4 से बड़ी है? 2   3   7",
                "answer_hi": "7",
            },
            {
                "question_hi": "7 के बाद कौन-सी संख्या आती है?",
                "answer_hi": "8",
            },
        ]

    else:
        questions = [
            {
                "question_hi": "सबसे छोटी संख्या चुनिए: 8   3   6   9",
                "answer_hi": "3",
            },
            {
                "question_hi": "5 और 8 के बीच कौन-कौन सी संख्याएँ हैं?",
                "answer_hi": "6, 7",
            },
            {
                "question_hi": (
                    "10 से पहले और 10 के बाद आने वाली संख्या लिखिए।"
                ),
                "answer_hi": "9 और 11",
            },
        ]

    return {
        "topic": topic,
        "date": date,
        "activity_type": "number_practice",
        "instruction_hi": "संख्याओं का अभ्यास कीजिए।",
        "difficulty": difficulty,
        "questions": questions,
    }


def create_flashcards(
    topic: str,
    date: str,
    difficulty: str,
) -> List[Dict[str, Any]]:
    """
    Generate flashcards for the selected topic.

    For the first prototype, the Numbers 1-10 topic gets
    real number cards. Other topics receive a generic card.
    """

    normalized_topic = topic.lower().strip()

    if "number" in normalized_topic:
        cards = []

        for number, word in NUMBER_WORDS_HINDI.items():
            cards.append(
                {
                    "topic": topic,
                    "date": date,
                    "front": str(number),
                    "back_hi": word,
                    "difficulty": difficulty,
                }
            )

        return cards

    return [
        {
            "topic": topic,
            "date": date,
            "front": topic,
            "back_hi": f"आज का विषय: {topic}",
            "difficulty": difficulty,
        }
    ]