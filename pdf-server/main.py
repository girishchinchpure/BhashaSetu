from datetime import datetime
from typing import List, Literal

from fastapi import FastAPI
from pydantic import BaseModel

from generator import generate_worksheet, generate_flashcards


app = FastAPI(
    title="BhashaSetu API",
    version="1.0.0",
    description="Backend API for BhashaSetu worksheet and flashcard generation.",
)


# ---------------------------------------------------------
# Health check
# ---------------------------------------------------------

@app.get("/health")
def health():
    return {
        "status": "ok",
        "service": "bhashasetu-api",
        "time": datetime.now().isoformat(),
    }


# ---------------------------------------------------------
# Request models
# ---------------------------------------------------------

class TopicItem(BaseModel):
    topic: str
    date: str


class GenerateMaterialRequest(BaseModel):
    curriculum: Literal["NCERT", "JCERT"]
    class_name: str
    subject: str
    topics: List[TopicItem]
    difficulty: Literal["Easy", "Medium", "Hard"]
    output: List[Literal["worksheet", "flashcards"]]
    language: Literal["Santhali"]


# ---------------------------------------------------------
# Extract translatable Hindi text
# ---------------------------------------------------------

def extract_translation_items(materials):
    """
    Extract every Hindi text value that the Android app
    will eventually need to translate to Santhali.
    """

    translation_items = []

    # Worksheet content
    worksheet = materials.get("worksheet")

    if worksheet:
        for activity in worksheet.get("activities", []):

            instruction_hi = activity.get("instruction_hi")

            if instruction_hi:
                translation_items.append({
                    "type": "instruction",
                    "text_hi": instruction_hi,
                })

            for question in activity.get("questions", []):

                question_hi = question.get("question_hi")

                if question_hi:
                    translation_items.append({
                        "type": "question",
                        "text_hi": question_hi,
                    })

                answer_hi = question.get("answer_hi")

                if answer_hi:
                    translation_items.append({
                        "type": "answer",
                        "text_hi": answer_hi,
                    })

                for option in question.get("options_hi", []):
                    translation_items.append({
                        "type": "option",
                        "text_hi": option,
                    })

    # Flashcard content
    flashcards = materials.get("flashcards")

    if flashcards:
        for card in flashcards.get("cards", []):

            back_hi = card.get("back_hi")

            if back_hi:
                translation_items.append({
                    "type": "flashcard",
                    "text_hi": back_hi,
                })

    return translation_items


# ---------------------------------------------------------
# Material generation
# ---------------------------------------------------------

@app.post("/generate-material")
def generate_material(request: GenerateMaterialRequest):

    topic_data = [
        {
            "topic": item.topic,
            "date": item.date,
        }
        for item in request.topics
    ]

    materials = {}

    # Generate worksheet
    if "worksheet" in request.output:
        materials["worksheet"] = generate_worksheet(
            class_name=request.class_name,
            subject=request.subject,
            topics=topic_data,
            difficulty=request.difficulty,
        )

    # Generate flashcards
    if "flashcards" in request.output:
        materials["flashcards"] = generate_flashcards(
            class_name=request.class_name,
            subject=request.subject,
            topics=topic_data,
            difficulty=request.difficulty,
        )

    # Extract Hindi strings for later translation
    translation_items = extract_translation_items(materials)

    return {
        "status": "success",
        "message": "BhashaSetu generated the requested educational material.",
        "request": {
            "curriculum": request.curriculum,
            "class": request.class_name,
            "subject": request.subject,
            "topics": topic_data,
            "difficulty": request.difficulty,
            "language": request.language,
        },
        "materials": materials,
        "translation_queue": translation_items,
    }