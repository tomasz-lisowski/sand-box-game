package sandbox.simulation

import com.badlogic.gdx.math.MathUtils
import scala.util.Random

class Cell(var mat: Material = Air) {
  var updated: Int = 0
  var dataA: Int = Cell.randAlpha()
  var dataB: Int = -1

  def update(
      get: CardinalDir => Cell,
      set: (CardinalDir, Cell) => Unit
  ): Unit = {
    mat match {
      case NuclearPasta => updateNuclearPasta()
      case Stone        => updateStone()
      case Air          => updateAir()
      case Sand         => updateSand()
      case Water        => updateWater()
      case Oil          => updateOil()
      case Fire         => updateFire()
      case Smoke        => updateSmoke()
      case BurningOil   => updateBurningOil()
      case SmokeSoot    => updateSmokeSoot()
      case Lava         => updateLava()
      case WaterVapor   => updateWaterVapor()
      case Seed         => updateSeed()
      case Wood         => updateWood()
      case BurningWood  => updateBurningWood()
      case Copper       => updateCopper()
      case CopperOxide  => updateCopperOxide()
      case HClAcid      => updateHClAcid()
    }

    /*=== Start of material simulation code ===*/
    def updateNuclearPasta(): Unit = {
      // dataA = color breathing and alpha
      // dataB = color breathing flags (3 hex digits, 0xf = increase, 0x0 = decrease)

      // Color breathing effect
      // Init
      if (dataB == -1) {
        dataB = 0x000
        dataA = 0x000000ff
      }
      // Max before overflow to higher order byte
      val bMaxDelta: Int = 0xff - ((NuclearPasta.color >>> 8) & 0xff)
      val gMaxDelta: Int = 0xff - ((NuclearPasta.color >>> (8 + 8)) & 0xff)
      val rMaxDelta: Int = 0xff - ((NuclearPasta.color >>> (8 + 8 + 8)) & 0xff)

      var bDelta: Int = (dataA >>> 8) & 0xff
      var gDelta: Int = (dataA >>> (8 + 8)) & 0xff
      var rDelta: Int = (dataA >>> (8 + 8 + 8)) & 0xff

      dataA = (stepColor(rDelta, rMaxDelta, 0xf00) << (8 + 8 + 8)) |
        (stepColor(gDelta, gMaxDelta, 0x0f0) << (8 + 8)) |
        (stepColor(rDelta, rMaxDelta, 0x00f) << (8)) |
        0xff

      def stepColor(initDelta: Int, maxDelta: Int, dataBMask: Int): Int = {
        var deltaColor: Int = initDelta
        // Determine if color should increase/decrease
        if (deltaColor == maxDelta) dataB &= ~dataBMask
        else if (deltaColor == 0) dataB |= dataBMask

        // Apply color change
        if ((dataB & dataBMask) == 0) deltaColor -= 1
        else if ((dataB & dataBMask) > 0) deltaColor += 1

        // Clamp to 0-maxDelta
        if (deltaColor < 0) 0
        else if (deltaColor > maxDelta) maxDelta
        else deltaColor
      }
    }

    def updateStone(): Unit = {
      // dataA = color alpha
      // dataB = unused
      move(simMotionSolid())
    }

    def updateAir(): Unit = {
      // dataA = color alpha (always 255)
      // dataB = unused
      if (dataA != 255) dataA = 255 // Uniform color
      move(simMotionGas())
    }

    def updateSand(): Unit = {
      // dataA = color alpha
      // dataB = unused
      move(simMotionGranularSolid())
    }

    def updateWater(): Unit = {
      // dataA = color alpha
      // dataB = unused
      val chanceToCorrodeStone = 0.001f

      // Pulse alpha
      if (dataA > 200) dataA -= 1
      else dataA = 255

      val lavaInNeighborhood = matInNeighborhood(Lava)
      val stoneInNeighborhood = matInNeighborhood(Stone)
      if (lavaInNeighborhood != Center) {
        changeMaterial(WaterVapor)
        get(lavaInNeighborhood).changeMaterial(Stone)
      } else {
        if (stoneInNeighborhood != Center && Cell.applyChance(chanceToCorrodeStone)) {
          get(stoneInNeighborhood).changeMaterial(Sand)
        }
        move(simMotionLiquid())
      }
    }

    def updateOil(): Unit = {
      // dataA = color alpha
      // dataB = if alpha should increase (=1) or decrease (=0) in a step

      // Pulse color lightness
      if (dataA == 255) dataB = 0
      else if (dataA <= 200) dataB = 1
      if (dataB == 0) dataA -= 1
      else if (dataB == 1) dataA += 1

      if (
        matInNeighborhood(Fire) != Center ||
        matInNeighborhood(BurningOil) != Center ||
        matInNeighborhood(Lava) != Center
      ) {
        changeMaterial(BurningOil)
      } else {
        move(simMotionLiquid())
      }
    }

    def updateFire(): Unit = {
      // dataA = color alpha
      // dataB = lifetime before becoming air
      val chanceToEmitSmoke = 0.2f

      // Lifetime
      if (dataB == -1) dataB = 16
      else if (dataB > 0) dataB -= 1

      if (dataB > 0) {
        if (dataB < 5 && Cell.applyChance(chanceToEmitSmoke)) tryEmitMat(Smoke)
        move(simMotionGas())
        dataB -= 1
      } else {
        changeMaterial(Air)
      }
    }

    def updateSmoke(): Unit = {
      // dataA = color alpha
      // dataB = lifetime before becoming air

      // Lifetime
      if (dataB == -1) dataB = 10
      else if (dataB > 0) dataB -= 1

      if (dataB > 0) {
        move(simMotionGas())
        dataB -= 1
      } else {
        changeMaterial(Air)
      }
    }

    def updateBurningOil(): Unit = {
      // dataA = color alpha
      // dataB = lifetime before becoming soot
      val chanceToEmitSoot = 0.5f

      // Lifetime
      if (dataB == -1) dataB = 10
      if (dataB > 0 && matInNeighborhood(Air) != Center) dataB -= 1 // Can't burn without air

      if (Cell.applyChance(chanceToEmitSoot)) tryEmitMat(SmokeSoot)
      if (dataB > 0) {
        move(simMotionGas())
      } else {
        changeMaterial(SmokeSoot)
      }
    }

    def updateSmokeSoot(): Unit = {
      // dataA = color alpha
      // dataB = lifetime before becoming smoke

      // Lifetime
      if (dataB == -1) dataB = 300
      else if (dataB > 0) dataB -= 1

      if (dataB > 0) {
        move(simMotionGas())
      } else {
        changeMaterial(Smoke)
      }
    }

    def updateLava(): Unit = {
      // dataA = color alpha
      // dataB = lifetime before coolding down to stone

      // Lifetime
      if (dataB == -1) dataB = 2000
      else if (dataB > 0 && matInNeighborhood(Lava, true) != Center)
        dataB -= 1 // Can't cooldown if submerged in lava

      // Sawtooth alpha
      if (dataA > 210) dataA -= 1
      else dataA = 255

      if (dataB > 0) {
        move(simMotionLiquid())
      } else {
        changeMaterial(Stone)
      }
    }

    def updateWaterVapor(): Unit = {
      // dataA = color alpha
      // dataB = lifetime before condensing to water

      // Lifetime
      if (dataB == -1) dataB = 800
      else if (dataB > 0) dataB -= 1

      // Random alpha
      if (dataA < 240) dataA = Cell.randAlpha()

      if (dataB > 0) {
        move(simMotionGas())
      } else {
        changeMaterial(Water)
      }
    }

    def updateSeed(): Unit = {
      // dataA = color alpha
      // dataB = unused
      val chanceToBurn = 0.5f

      val canStartBurning: Boolean =
        matInNeighborhood(Fire) != Center ||
          matInNeighborhood(Lava) != Center
      if (canStartBurning) {
        if (Cell.applyChance(chanceToBurn)) {
          changeMaterial(Fire)
        }
      } else {
        move(simMotionGranularSolid())
      }
    }

    def updateWood(): Unit = {
      // dataA = color alpha
      // dataB = unused
      val chanceToBurn = 0.1f

      val canStartBurning: Boolean =
        matInNeighborhood(Fire) != Center ||
          matInNeighborhood(Lava) != Center ||
          matInNeighborhood(BurningWood) != Center
      if (canStartBurning) {
        if (Cell.applyChance(chanceToBurn)) {
          changeMaterial(BurningWood)
        }
      }
    }

    def updateBurningWood(): Unit = {
      // dataA = color alpha
      // dataB = lifetime before burning up
      val chanceToEmitSmoke = 0.1f
      val chanceToEmitFire = 0.9f

      // Lifetime
      if (dataB == -1) dataB = 300
      else if (dataB > 0) dataB -= 1

      if (dataB == 0) {
        changeMaterial(Air)
      } else {
        if (Cell.applyChance(chanceToEmitFire)) tryEmitMat(Fire)
        if (Cell.applyChance(chanceToEmitSmoke)) tryEmitMat(Smoke)
      }
    }

    def updateCopper(): Unit = {
      // dataA = color alpha
      // dataB = unused
      val chanceToOxidize = 0.002f

      if (dataA != 255) dataA = 255 // Uniform color

      // Oxidize
      if (matInNeighborhood(Air) != Center && Cell.applyChance(chanceToOxidize)) {
        changeMaterial(CopperOxide)
      }
    }

    def updateCopperOxide(): Unit = {
      // dataA = color alpha
      // dataB = unused
    }

    def updateHClAcid(): Unit = {
      // dataA = color alpha
      // dataB = unused
      val chanceToEmitSmokeOnCorrosion = 0.2f

      // Sawtooth alpha
      if (dataA > 230) dataA -= 1
      else dataA = 255

      // Corrode any material that's not air or hcl acid or nuclear pasta
      val toCorrode = CardinalDir.all.filter(dir =>
        get(dir).mat != Air &&
          get(dir).mat != HClAcid &&
          get(dir).mat != NuclearPasta
      )
      if (toCorrode.length != 0) {
        get(toCorrode(0)).changeMaterial(Air)
        changeMaterial(Air)
        if (Cell.applyChance(chanceToEmitSmokeOnCorrosion)) {
          tryEmitMat(Smoke)
        }
      } else move(simMotionLiquid())
    }
    /*=== End of material simulation code ===*/

    def tryEmitMat(mat: Material): Unit = {
      val spaceToEmit: CardinalDir = matInNeighborhood(Air)
      if (spaceToEmit != Center) {
        get(spaceToEmit).changeMaterial(mat)
      }
    }

    // This causes a memory leak (significant only because of how many cells are calling it)
    /* When 'not' is false, returns first dir that contains the 'mat',
    if 'not' is true, returns first dir that does not contain the 'mat' */
    def matInNeighborhood(mat: Material, not: Boolean = false): CardinalDir = {
      CardinalDir.all.foreach(dir => {
        if (!not && get(dir).mat == mat) return dir
        if (not && get(dir).mat != mat) return dir
      })
      return Center
    }

    /*=== Start of motion simulation code ===*/
    /* Making these into non-local functions made the simulation
    very slow ceteris paribus, so it's best they are kept here. */

    def move(moveDir: CardinalDir): Unit = {
      // Limit number of times a cell can move in one step to 2
      if (moveDir != Center && get(moveDir).updated <= 1) {
        get(moveDir).updated += 1
        set(Center, get(moveDir))
        set(moveDir, this)
      }
    }

    def canDisplace(dir: CardinalDir): Boolean = {
      // Can only move diagonally if the cell can displace either it's left/right or up/down neighbor
      val canMoveDiagonally: Boolean = dir match {
        case SouthEast => canDisplace(South) || canDisplace(East)
        case SouthWest => canDisplace(South) || canDisplace(West)
        case NorthEast => canDisplace(North) || canDisplace(East)
        case NorthWest => canDisplace(North) || canDisplace(West)
        case _         => true
      }
      mat.canDisplace(get(dir).mat, dir) && get(dir).updated <= 1 && canMoveDiagonally
    }

    def simMotionGranularSolid(): CardinalDir = {
      if (canDisplace(South)) South
      else if (canDisplace(SouthEast) && canDisplace(SouthWest))
        Cell.randChoice(SouthEast, SouthWest)
      else if (canDisplace(SouthEast)) SouthEast
      else if (canDisplace(SouthWest)) SouthWest
      else Center
    }

    def simMotionSolid(): CardinalDir = {
      if (canDisplace(South)) South
      else Center
    }

    def simMotionLiquid(): CardinalDir = {
      def moveDestHoriz(): CardinalDir = {
        if (canDisplace(South)) South
        else if (canDisplace(SouthEast) && canDisplace(SouthWest))
          Cell.randChoice(SouthEast, SouthWest)
        else if (canDisplace(SouthEast)) SouthEast
        else if (canDisplace(SouthWest)) SouthWest
        else Center
      }

      def moveDestVert(): CardinalDir = {
        if (canDisplace(East) && canDisplace(West))
          Cell.randChoice(East, West)
        else if (canDisplace(East)) East
        else if (canDisplace(West)) West
        else Center
      }

      lazy val moveVertOrHoriz: Float = MathUtils.random(0f, 1f)
      // Liquids flow or fall based on a probability
      if (moveVertOrHoriz < 0.9f) {
        val destDir: CardinalDir = moveDestHoriz()
        if (destDir == Center) moveDestVert()
        else destDir
      } else {
        val destDir: CardinalDir = moveDestVert()
        if (destDir == Center) moveDestHoriz()
        else destDir
      }
    }

    def simMotionGas(): CardinalDir = {
      lazy val moveVertOrHoriz: Int = MathUtils.random(0, 1)
      if (moveVertOrHoriz == 0) {
        if (canDisplace(South)) South
        else if (canDisplace(SouthEast) && canDisplace(SouthWest))
          Cell.randChoice(SouthEast, SouthWest)
        else if (canDisplace(SouthEast)) SouthEast
        else if (canDisplace(SouthWest)) SouthWest
        else Center
      } else {
        if (canDisplace(East) && canDisplace(West))
          Cell.randChoice(East, West)
        else if (canDisplace(East)) East
        else if (canDisplace(West)) West
        else Center
      }
    }
    /*=== End of motion simulation code ===*/
  }

  def changeMaterial(newMat: Material): Unit = {
    mat = newMat
    updated = 0
    dataA = Cell.randAlpha()
    dataB = -1
  }

}

object Cell {
  def randAlpha(): Int = 200 + MathUtils.random(0, 55)
  def randChoice[T](a: T, b: T): T = {
    if (MathUtils.random(0, 1) == 0) a else b
  }
  def applyChance(p: Float): Boolean = {
    p >= MathUtils.random(0f, 1f)
  }
}
